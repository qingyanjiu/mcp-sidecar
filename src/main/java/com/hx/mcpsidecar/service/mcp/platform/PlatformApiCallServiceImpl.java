package com.hx.mcpsidecar.service.mcp.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hx.mcpsidecar.model.McpServerProperties;
import com.hx.mcpsidecar.model.PlatformResponse;
import com.hx.mcpsidecar.service.AbstractApiCallService;
import com.hx.mcpsidecar.service.IApiCallService;
import com.hx.mcpsidecar.utils.LogSanitizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Cipher;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
public class PlatformApiCallServiceImpl extends AbstractApiCallService implements IApiCallService<PlatformResponse> {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private McpServerProperties mcpApiProperties;

    /** 平台登录凭据；从 .env / 环境变量注入，禁止在代码中硬编码。 */
    @Value("${mcp.servers.platform.login-username:}")
    private String loginUsername;

    @Value("${mcp.servers.platform.login-password:}")
    private String loginPassword;

    @Value("${mcp.servers.platform.login-type:1}")
    private String loginType;

    @Value("${mcp.servers.platform.captcha-id:}")
    private String captchaId;

    @Value("${mcp.servers.platform.captcha-code:}")
    private String captchaCode;

    /** 平台未启用 RSA 时是否允许明文登录；默认 false，生产环境不应打开。 */
    @Value("${mcp.servers.platform.allow-plaintext-login:false}")
    private boolean allowPlaintextLogin;

    private ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getBaseUrl() {
        String baseUrl = mcpApiProperties.getServers().get(getServerName()).get("baseUrl").toString();
        return baseUrl;
    }

    @Override
    public RestTemplate getRestTemplate() {
        return restTemplate;
    }

    @Override
    public void login() {
        String baseUrl = getBaseUrl();
        Assert.hasText(baseUrl, "未配置平台地址 mcp.servers.platform.baseUrl / MCP_PLATFORM_BASE_URL");
        Assert.hasText(loginUsername, "未配置平台登录账号 mcp.servers.platform.login-username");
        Assert.hasText(loginPassword, "未配置平台登录密码 mcp.servers.platform.login-password");

        Map<String, String> params = new LinkedHashMap<>();
        params.put("username", loginUsername);
        params.put("password", loginPassword);
        params.put("login_type", loginType);
        params.put("captcha_id", captchaId);
        params.put("captcha_code", captchaCode);

        // 平台 config 里 sslLogin=on 时，username/password/login_type 需要 RSA 加密后提交；
        // 验证码参数不经过 decryptLoginValue，保持明文。
        String publicKey = getLoginPublicKey(baseUrl);
        if (publicKey != null) {
            params.put("username", rsaEncrypt(publicKey, params.get("username")));
            params.put("password", rsaEncrypt(publicKey, params.get("password")));
            params.put("login_type", rsaEncrypt(publicKey, params.get("login_type")));
        }

        // 凭据放 POST body，避免出现在 URL、网关访问日志和浏览器历史中。
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        params.forEach(form::add);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        ResponseEntity<PlatformResponse> responseEntity = restTemplate.postForEntity(
                baseUrl + "/login", new HttpEntity<>(form, headers), PlatformResponse.class);
        PlatformResponse resp = responseEntity.getBody();
        Assert.isTrue(resp != null && resp.getData() != null && "1".equals(resp.getStatus()), "登录请求失败");
        String accessToken = ((Map) resp.getData()).get("token").toString();
        long timestamp = new Date().getTime();
        tokenMap.put("token", accessToken);
        tokenMap.put("expire", timestamp + getTokenExpiration());
    }

    /**
     * 读取平台的登录加密配置。返回 null 表示允许明文登录（仅当显式开启兼容开关）；
     * 平台未启用 RSA 时默认抛异常，避免把账号口令明文提交出去。
     */
    private String getLoginPublicKey(String baseUrl) {
        try {
            ResponseEntity<PlatformResponse> responseEntity = restTemplate.getForEntity(
                    baseUrl + "/noLogin/getLoginInfo", PlatformResponse.class);
            PlatformResponse resp = responseEntity.getBody();
            if (resp == null || resp.getData() == null) {
                return allowPlaintext("平台未返回登录加密配置");
            }
            Map<String, Object> data = objectMapper.convertValue(resp.getData(), Map.class);
            if (!"on".equalsIgnoreCase(String.valueOf(data.get("ssl")))) {
                return allowPlaintext("平台未开启 RSA 登录加密");
            }
            if (!"rsa".equalsIgnoreCase(String.valueOf(data.get("sslType")))) {
                return allowPlaintext("平台登录加密方式[" + data.get("sslType") + "]暂不支持");
            }
            Object publicKey = data.get("publicKey");
            if (publicKey == null) {
                return allowPlaintext("平台未返回 RSA 公钥");
            }
            return publicKey.toString();
        } catch (ResourceAccessException e) {
            // 连不上平台（Connection refused / 超时），退回明文登录没有意义，直接把原因抛出去，
            // 否则只会看到一句“按明文登录”，真正的故障（平台没起）被掩盖。
            Throwable cause = e.getMostSpecificCause();
            String reason = cause == null ? e.getClass().getSimpleName()
                    : cause.getClass().getSimpleName() + ": " + cause.getMessage();
            throw new IllegalStateException("无法连接平台 " + baseUrl + "（" + reason + "）", e);
        } catch (RestClientException e) {
            // 平台可达但 getLoginInfo 返回非 2xx（老版本平台可能没有这个接口）
            return allowPlaintext("获取平台登录公钥失败[" + e.getClass().getSimpleName() + "]");
        }
    }

    /**
     * 只有显式开启 allow-plaintext-login 才允许回退明文；否则直接拒绝，避免配置缺失时静默降级。
     */
    private String allowPlaintext(String reason) {
        if (!allowPlaintextLogin) {
            throw new IllegalStateException(reason
                    + "，已拒绝明文提交凭据；如确需兼容，设置 mcp.servers.platform.allow-plaintext-login=true");
        }
        log.warn("{}，按明文登录（allow-plaintext-login=true）", reason);
        return null;
    }

    /**
     * 与平台 EncryptUtil 一致：RSA/ECB/PKCS1Padding，公钥为 X.509 Base64，输出 Base64。
     */
    private static String rsaEncrypt(String publicKeyBase64, String plainText) {
        try {
            byte[] encoded = Base64.getDecoder().decode(publicKeyBase64);
            PublicKey publicKey = KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(encoded));
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            return Base64.getEncoder().encodeToString(
                    cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("登录参数RSA加密失败: " + e.getClass().getSimpleName());
        }
    }

    @Override
    public Object doGetCall(String url) {
        log.info("MCP调用后端接口 GET [{}]", url);
        ResponseEntity<Map> response = executeWithAuthRetry("GET", url, null);
        PlatformResponse platformResp = objectMapper.convertValue(response.getBody(), PlatformResponse.class);
        if (platformResp == null || !"1".equals(platformResp.getStatus())) {
            log.warn("MCP调用后端接口 GET [{}] 返回业务失败: status={}, msg={}", url,
                    platformResp == null ? null : platformResp.getStatus(),
                    platformResp == null ? null : platformResp.getMsg());
        } else {
            log.info("MCP调用后端接口 GET [{}] 成功", url);
        }
        return platformResp == null ? null : platformResp.getData();
    }

    @Override
    public Object doGetCallWithTokenInHeader(String url, String token) {
        ResponseEntity<Map> response = super.getWithTokenInHeader(url, token);
        PlatformResponse platformResp = objectMapper.convertValue(response.getBody(), PlatformResponse.class);
        Object data = platformResp == null ? null : platformResp.getData();
        // url 里带 token，日志必须打码；这里把平台的真实判定（data=200 才是已登录）打出来，
        // 否则 HTTP 200 会被误认为鉴权通过。
        log.info("token鉴权请求[{}]返回: httpStatus={}, status={}, msg={}, data={}",
                LogSanitizer.maskUri(url), response.getStatusCode().value(),
                platformResp == null ? null : platformResp.getStatus(),
                platformResp == null ? null : platformResp.getMsg(),
                data);
        return data;
    }

    @Override
    public Object doPostCall(String url, Map<String, Object> data) {
        // 业务参数可能含设备、人员、场景等敏感信息，只记录字段名和数量，不落值。
        log.info("MCP调用后端接口 POST [{}], 参数键: {}, 参数个数: {}",
                url, data == null ? "[]" : data.keySet(), data == null ? 0 : data.size());
        ResponseEntity<Map> response = executeWithAuthRetry("POST", url, data);
        PlatformResponse platformResp = objectMapper.convertValue(response.getBody(), PlatformResponse.class);
        if (platformResp == null) {
            log.warn("MCP调用后端接口 POST [{}] 返回内容不是平台响应结构", url);
            return null;
        }
        log.info("MCP调用后端接口 POST [{}] 成功", url);
        return platformResp.getData();
    }

    /**
     * 带登录态自愈的请求：平台重启后会丢掉服务端登录会话（平台登录态存在本地 caffeine 缓存里），
     * 本地缓存的 token 还在有效期内但平台已不认，表现为 HTTP 302/401 或业务 status=302。
     * 检测到后清掉本地 token、重新登录并重试一次，避免必须重启 sidecar。
     */
    private ResponseEntity<Map> executeWithAuthRetry(String method, String url, Map<String, Object> body) {
        ResponseEntity<Map> response;
        try {
            response = send(method, url, body);
        } catch (RestClientResponseException e) {
            if (!isAuthFailure(e.getStatusCode().value())) {
                throw httpError(method, url, e);
            }
            log.warn("MCP调用后端接口 {} [{}] 返回 HTTP {}，清掉本地 token 重新登录后重试一次",
                    method, url, e.getStatusCode().value());
            response = null;
        } catch (ResourceAccessException e) {
            throw accessError(method, url, e);
        }

        if (response != null && !isAuthLost(response.getBody())) {
            return response;
        }
        if (response != null) {
            log.warn("MCP调用后端接口 {} [{}] 返回未登录状态(status={})，清掉本地 token 重新登录后重试一次",
                    method, url, statusOf(response.getBody()));
        }

        invalidateToken();

        ResponseEntity<Map> retried;
        try {
            retried = send(method, url, body);
        } catch (RestClientResponseException e) {
            throw httpError(method, url, e);
        } catch (ResourceAccessException e) {
            throw accessError(method, url, e);
        }
        if (isAuthLost(retried.getBody())) {
            log.error("MCP调用后端接口 {} [{}] 重新登录后仍返回未登录状态(status={})",
                    method, url, statusOf(retried.getBody()));
            throw new IllegalStateException("平台鉴权失效，重新登录后仍被拒绝（" + method + " " + pathOf(url) + "）");
        }
        return retried;
    }

    private ResponseEntity<Map> send(String method, String url, Map<String, Object> body) {
        return "POST".equals(method) ? super.post(url, body) : super.get(url);
    }

    private void invalidateToken() {
        tokenMap.remove("token");
        tokenMap.remove("expire");
    }

    private static boolean isAuthFailure(int status) {
        return status == 401 || status == 403;
    }

    /** 平台未登录时返回 HTTP 302 + {"status":"302","msg":"重定向","data":".../login.html"}。 */
    private boolean isAuthLost(Map body) {
        String status = statusOf(body);
        return "302".equals(status) || "401".equals(status);
    }

    private String statusOf(Map body) {
        if (body == null) {
            return null;
        }
        try {
            PlatformResponse resp = objectMapper.convertValue(body, PlatformResponse.class);
            return resp == null ? null : resp.getStatus();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 平台返回非 2xx 时统一处理：日志里保留完整 URL、状态码和响应体片段（便于排查），
     * 抛给 MCP 工具的错误只包含「方法 + 路径 + 状态码 + 简短原因」，
     * 避免把 Whitelabel 之类的整页 HTML 当作查询结果透传给大模型。
     */
    private IllegalStateException httpError(String method, String url, RestClientResponseException e) {
        int status = e.getStatusCode().value();
        log.error("MCP调用后端接口 {} [{}] 返回 HTTP {}: {}", method, url, status,
                abbreviate(e.getResponseBodyAsString(), 300));
        return new IllegalStateException("平台接口 " + method + " " + pathOf(url)
                + " 返回 HTTP " + status + "（" + statusHint(status) + "）");
    }

    /** 连不上平台（拒绝连接、读超时等）时给出可读原因。 */
    private IllegalStateException accessError(String method, String url, ResourceAccessException e) {
        Throwable cause = e.getMostSpecificCause();
        String reason = cause == null ? e.getMessage()
                : cause.getClass().getSimpleName() + ": " + cause.getMessage();
        log.error("MCP调用后端接口 {} [{}] 连接平台失败: {}", method, url, reason);
        return new IllegalStateException("无法连接平台（" + method + " " + pathOf(url) + "）：" + reason, e);
    }

    private static String statusHint(int status) {
        if (status == 400) {
            return "请求参数错误";
        }
        if (status == 401 || status == 403) {
            return "鉴权失败";
        }
        if (status == 404) {
            return "接口不存在或路径不对";
        }
        if (status == 429) {
            return "请求过于频繁";
        }
        if (status >= 500) {
            return "平台服务异常";
        }
        return "平台返回异常状态";
    }

    /** 只取路径，避免把平台内网地址带进给大模型的错误信息。 */
    private static String pathOf(String url) {
        try {
            String path = URI.create(url).getPath();
            return path == null || path.isEmpty() ? url : path;
        } catch (IllegalArgumentException e) {
            return url;
        }
    }

    /** 日志单行化并截断，防止整页 HTML 刷屏。 */
    private static String abbreviate(String text, int max) {
        if (text == null) {
            return "";
        }
        String oneLine = text.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= max ? oneLine : oneLine.substring(0, max) + "...";
    }
}

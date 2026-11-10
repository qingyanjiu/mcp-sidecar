package com.hx.mcpsidecar.service.mcp.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hx.mcpsidecar.model.McpServerProperties;
import com.hx.mcpsidecar.model.PlatformResponse;
import com.hx.mcpsidecar.service.AbstractApiCallService;
import com.hx.mcpsidecar.service.IApiCallService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Cipher;
import java.net.URI;
import java.net.URLEncoder;
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

        Map<String, String> params = new LinkedHashMap<>();
        params.put("username", "hexinadmin");
        params.put("password", "Hxkj@2026");
        params.put("login_type", "1");
        params.put("captcha_id", "123");
        params.put("captcha_code", "d0080921765faab7b6a4d264dcf43f01");

        // 平台 config 里 sslLogin=on 时，username/password/login_type 需要 RSA 加密后提交；
        // 验证码参数不经过 decryptLoginValue，保持明文。
        String publicKey = getLoginPublicKey(baseUrl);
        if (publicKey != null) {
            params.put("username", rsaEncrypt(publicKey, params.get("username")));
            params.put("password", rsaEncrypt(publicKey, params.get("password")));
            params.put("login_type", rsaEncrypt(publicKey, params.get("login_type")));
        }

        URI uri = URI.create(baseUrl + "/login?" + toQueryString(params));
        ResponseEntity<PlatformResponse> responseEntity = restTemplate.postForEntity(uri, null, PlatformResponse.class);
        PlatformResponse resp = responseEntity.getBody();
        Assert.isTrue(resp != null && resp.getData() != null && "1".equals(resp.getStatus()), "登录请求失败");
        String accessToken = ((Map) resp.getData()).get("token").toString();
        long timestamp = new Date().getTime();
        tokenMap.put("token", accessToken);
        tokenMap.put("expire", timestamp + getTokenExpiration());
    }

    /**
     * 读取平台的登录加密配置。返回 null 表示平台未开启 RSA 加密（或读取失败），按明文登录。
     */
    private String getLoginPublicKey(String baseUrl) {
        try {
            ResponseEntity<PlatformResponse> responseEntity = restTemplate.getForEntity(
                    baseUrl + "/noLogin/getLoginInfo", PlatformResponse.class);
            PlatformResponse resp = responseEntity.getBody();
            if (resp == null || resp.getData() == null) {
                return null;
            }
            Map<String, Object> data = objectMapper.convertValue(resp.getData(), Map.class);
            if (!"on".equalsIgnoreCase(String.valueOf(data.get("ssl")))) {
                return null;
            }
            if (!"rsa".equalsIgnoreCase(String.valueOf(data.get("sslType")))) {
                log.warn("平台登录加密方式[{}]暂不支持，按明文登录", data.get("sslType"));
                return null;
            }
            Object publicKey = data.get("publicKey");
            return publicKey == null ? null : publicKey.toString();
        } catch (RestClientException e) {
            log.warn("获取平台登录公钥失败[{}]，按明文登录", e.getClass().getSimpleName());
            return null;
        }
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

    private static String toQueryString(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    @Override
    public Object doGetCall(String url) {
        log.info("MCP调用后端接口 GET [{}]", url);
        ResponseEntity<Map> response = super.get(url);
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
        log.info("GET无登录请求[{}]成功获取到数据", url);
        PlatformResponse platformResp = objectMapper.convertValue(response.getBody(), PlatformResponse.class);
        return platformResp.getData();
    }

    @Override
    public Object doPostCall(String url, Map<String, Object> data) {
        log.info("MCP调用后端接口 POST [{}], 参数: {}", url, data);
        ResponseEntity<Map> response = super.post(url, data);
        log.info("MCP调用后端接口 POST [{}] 成功", url);
        PlatformResponse platformResp = objectMapper.convertValue(response.getBody(), PlatformResponse.class);
        return platformResp.getData();
    }
}

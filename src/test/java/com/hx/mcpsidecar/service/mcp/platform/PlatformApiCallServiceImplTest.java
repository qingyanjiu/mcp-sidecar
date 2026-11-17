package com.hx.mcpsidecar.service.mcp.platform;

import com.hx.mcpsidecar.model.McpServerProperties;
import com.hx.mcpsidecar.model.PlatformResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("平台接口调用错误处理")
class PlatformApiCallServiceImplTest {

    private static final String BASE = "http://localhost:9500/platform";

    private PlatformApiCallServiceImpl service;
    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        service = new PlatformApiCallServiceImpl();
        restTemplate = mock(RestTemplate.class);
        ReflectionTestUtils.setField(service, "restTemplate", restTemplate);

        // 预置一个未过期的 token，避免触发真实登录
        @SuppressWarnings("unchecked")
        Map<String, Object> tokenMap =
                (Map<String, Object>) ReflectionTestUtils.getField(service, "tokenMap");
        tokenMap.put("token", "test-token");
        tokenMap.put("expire", System.currentTimeMillis() + 3_600_000L);

        McpServerProperties properties = new McpServerProperties();
        properties.setServers(Map.of("platform", Map.of("baseUrl", BASE)));
        ReflectionTestUtils.setField(service, "mcpApiProperties", properties);

        // 单元测试不启动 Spring，手动注入登录配置；真实值来自 .env，不写进测试代码。
        ReflectionTestUtils.setField(service, "loginUsername", "test-user");
        ReflectionTestUtils.setField(service, "loginPassword", "test-password");
        ReflectionTestUtils.setField(service, "loginType", "1");
        ReflectionTestUtils.setField(service, "captchaId", "test-captcha-id");
        ReflectionTestUtils.setField(service, "captchaCode", "test-captcha-code");
        ReflectionTestUtils.setField(service, "allowPlaintextLogin", true);
    }

    /** 模拟平台登录成功：getLoginInfo 按明文登录，/login 返回新 token。 */
    private void mockLoginSuccess() {
        PlatformResponse loginInfo = new PlatformResponse();
        loginInfo.setStatus("1");
        loginInfo.setData(Map.of("ssl", "off"));
        when(restTemplate.getForEntity(eq(BASE + "/noLogin/getLoginInfo"), eq(PlatformResponse.class)))
                .thenReturn(ResponseEntity.ok(loginInfo));

        PlatformResponse login = new PlatformResponse();
        login.setStatus("1");
        login.setMsg("success");
        login.setData(Map.of("token", "new-token"));
        when(restTemplate.postForEntity(eq(BASE + "/login"), any(HttpEntity.class), eq(PlatformResponse.class)))
                .thenReturn(ResponseEntity.ok(login));
    }

    private static Map<String, Object> loginRedirectBody() {
        return Map.of(
                "status", "302",
                "msg", "重定向",
                "data", BASE + "/login.html");
    }

    @Test
    @DisplayName("平台重启后返回 302，自动重新登录并重试一次")
    void get302_reloginAndRetry() {
        mockLoginSuccess();
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.status(302).body(loginRedirectBody()))
                .thenReturn(ResponseEntity.ok(Map.of("status", "1", "data", Map.of("centerArea", 100))));

        Object result = service.doGetCall(BASE + "/overview/basicInfo");

        assertEquals(Map.of("centerArea", 100), result);
        verify(restTemplate, times(1))
                .postForEntity(eq(BASE + "/login"), any(HttpEntity.class), eq(PlatformResponse.class));
    }

    @Test
    @DisplayName("重新登录后仍是 302 时明确报鉴权失效，不把登录地址当数据返回")
    void get302AfterRelogin_throwsAuthError() {
        mockLoginSuccess();
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.status(302).body(loginRedirectBody()))
                .thenReturn(ResponseEntity.status(302).body(loginRedirectBody()));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.doGetCall(BASE + "/overview/basicInfo"));

        assertEquals("平台鉴权失效，重新登录后仍被拒绝（GET /platform/overview/basicInfo）", ex.getMessage());
    }

    @Test
    @DisplayName("GET 404 只返回简短原因，不包含 HTML 响应体")
    void get404_returnsReadableMessageWithoutHtml() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY,
                        "<html><body><h1>Whitelabel Error Page</h1></body></html>"
                                .getBytes(StandardCharsets.UTF_8),
                        StandardCharsets.UTF_8));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.doGetCall(BASE + "/overview/deviceHealth"));

        assertEquals("平台接口 GET /platform/overview/deviceHealth 返回 HTTP 404（接口不存在或路径不对）",
                ex.getMessage());
        assertFalse(ex.getMessage().toLowerCase().contains("html"));
        assertFalse(ex.getMessage().contains("Whitelabel"));
    }

    @Test
    @DisplayName("POST 5xx 返回平台服务异常")
    void post5xx_returnsReadableMessage() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(HttpServerErrorException.create(
                        HttpStatus.BAD_GATEWAY, "Bad Gateway", HttpHeaders.EMPTY,
                        new byte[0], StandardCharsets.UTF_8));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.doPostCall(BASE + "/deviceInfo/listOnly", Map.of("pageNo", 1)));

        assertEquals("平台接口 POST /platform/deviceInfo/listOnly 返回 HTTP 502（平台服务异常）",
                ex.getMessage());
    }

    @Test
    @DisplayName("平台连不上时给出连接失败原因")
    void connectionFailure_returnsReadableMessage() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new ResourceAccessException("I/O error",
                        new ConnectException("Connection refused")));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.doGetCall(BASE + "/overview/basicInfo"));

        assertTrue(ex.getMessage().startsWith("无法连接平台（GET /platform/overview/basicInfo）："));
        assertTrue(ex.getMessage().contains("Connection refused"));
    }

    @Test
    @DisplayName("登录凭据放 POST body，不出现在 URL 中")
    void login_usesPostBody() {
        mockLoginSuccess();

        service.login();

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(
                eq(BASE + "/login"), captor.capture(), eq(PlatformResponse.class));
        HttpEntity<?> request = captor.getValue();
        assertTrue(request.getBody() instanceof MultiValueMap);
        @SuppressWarnings("unchecked")
        MultiValueMap<String, String> body = (MultiValueMap<String, String>) request.getBody();
        assertEquals("test-user", body.getFirst("username"));
        assertEquals("test-password", body.getFirst("password"));
        assertEquals("test-captcha-code", body.getFirst("captcha_code"));
    }

    @Test
    @DisplayName("平台未开启 RSA 时默认拒绝明文登录")
    void login_rejectsPlaintextByDefault() {
        ReflectionTestUtils.setField(service, "allowPlaintextLogin", false);

        PlatformResponse loginInfo = new PlatformResponse();
        loginInfo.setStatus("1");
        loginInfo.setData(Map.of("ssl", "off"));
        when(restTemplate.getForEntity(eq(BASE + "/noLogin/getLoginInfo"), eq(PlatformResponse.class)))
                .thenReturn(ResponseEntity.ok(loginInfo));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.login());

        assertTrue(ex.getMessage().contains("拒绝明文"));
        verify(restTemplate, times(0))
                .postForEntity(anyString(), any(HttpEntity.class), eq(PlatformResponse.class));
    }
}

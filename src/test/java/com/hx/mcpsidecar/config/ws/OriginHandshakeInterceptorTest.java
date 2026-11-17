package com.hx.mcpsidecar.config.ws;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@DisplayName("WebSocket Origin 校验")
class OriginHandshakeInterceptorTest {

    private final WebSocketHandler handler = mock(WebSocketHandler.class);

    private boolean handshake(OriginHandshakeInterceptor interceptor, String origin) {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/llm/chat");
        servletRequest.setScheme("http");
        servletRequest.setServerName("10.0.0.5");
        servletRequest.setServerPort(2222);
        servletRequest.addHeader(HttpHeaders.HOST, "10.0.0.5:2222");
        if (origin != null) {
            servletRequest.addHeader(HttpHeaders.ORIGIN, origin);
        }

        ServerHttpRequest request = new ServletServerHttpRequest(servletRequest);
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        ServerHttpResponse response = new ServletServerHttpResponse(servletResponse);

        boolean allowed = interceptor.beforeHandshake(
                request, response, handler, new HashMap<>());
        if (!allowed) {
            assertEquals(403, servletResponse.getStatus());
        }
        return allowed;
    }

    @Test
    @DisplayName("同主机、不同端口的页面放行")
    void allowsSameHostDifferentPort() {
        assertTrue(handshake(new OriginHandshakeInterceptor(""), "http://10.0.0.5:8080"));
    }

    @Test
    @DisplayName("其它主机的页面拒绝")
    void rejectsForeignHost() {
        assertFalse(handshake(new OriginHandshakeInterceptor(""), "http://evil.example:8080"));
    }

    @Test
    @DisplayName("白名单中的跨主机页面放行")
    void allowsConfiguredOrigin() {
        OriginHandshakeInterceptor interceptor =
                new OriginHandshakeInterceptor("http://10.0.0.6:8080, https://admin.example");
        assertTrue(handshake(interceptor, "http://10.0.0.6:8080"));
        assertTrue(handshake(interceptor, "https://admin.example"));
    }

    @Test
    @DisplayName("无 Origin 的内网服务端调用放行")
    void allowsMissingOrigin() {
        assertTrue(handshake(new OriginHandshakeInterceptor(""), null));
    }

    @Test
    @DisplayName("非法 Origin（如 null）拒绝")
    void rejectsInvalidOrigin() {
        assertFalse(handshake(new OriginHandshakeInterceptor(""), "null"));
    }
}

package com.hx.mcpsidecar.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("端口隔离过滤器")
class EndpointPortGuardFilterTest {

    private static final int PUBLIC_PORT = 2222;
    private static final int INTERNAL_PORT = 2223;

    private final EndpointPortGuardFilter filter =
            new EndpointPortGuardFilter(PUBLIC_PORT, INTERNAL_PORT);

    @Test
    @DisplayName("公网口放行 /llm/** 和 /ws/**")
    void publicPort_allowsLlmAndWs() throws Exception {
        assertAllowed(PUBLIC_PORT, "/llm/chat");
        assertAllowed(PUBLIC_PORT, "/llm/gen_doc");
        assertAllowed(PUBLIC_PORT, "/ws/scene");
    }

    @Test
    @DisplayName("公网口拒绝 /mcp/**")
    void publicPort_rejectsMcp() throws Exception {
        assertBlocked(PUBLIC_PORT, "/mcp/scene");
        assertBlocked(PUBLIC_PORT, "/mcp/device");
    }

    @Test
    @DisplayName("内网口放行 /mcp/**")
    void internalPort_allowsMcp() throws Exception {
        assertAllowed(INTERNAL_PORT, "/mcp/scene");
        assertAllowed(INTERNAL_PORT, "/mcp/devicequery");
    }

    @Test
    @DisplayName("内网口拒绝 /llm/** 和 /ws/**")
    void internalPort_rejectsLlmAndWs() throws Exception {
        assertBlocked(INTERNAL_PORT, "/llm/chat");
        assertBlocked(INTERNAL_PORT, "/ws/scene");
    }

    @Test
    @DisplayName("actuator 等其它端口不受影响")
    void otherPort_passesThrough() throws Exception {
        assertAllowed(9970, "/actuator/health");
    }

    private void assertAllowed(int port, String path) throws Exception {
        MockHttpServletRequest request = request(port, path);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertNotNull(chain.getRequest(), path + " 应放行");
    }

    private void assertBlocked(int port, String path) throws Exception {
        MockHttpServletRequest request = request(port, path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNull(chain.getRequest(), path + " 应被拦截");
        assertEquals(404, response.getStatus());
    }

    private static MockHttpServletRequest request(int port, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setLocalPort(port);
        return request;
    }
}

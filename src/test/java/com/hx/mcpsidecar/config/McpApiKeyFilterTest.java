package com.hx.mcpsidecar.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("MCP API Key 鉴权")
class McpApiKeyFilterTest {

    private static final int INTERNAL_PORT = 2223;
    private static final int PUBLIC_PORT = 2222;
    private static final String KEY = "test-mcp-key-0123456789";

    private final McpApiKeyFilter filter = new McpApiKeyFilter(KEY, INTERNAL_PORT);

    @Test
    @DisplayName("内网口缺少 key 返回 401，且不进入业务链")
    void missingKey_rejected() throws Exception {
        MockHttpServletRequest request = request(INTERNAL_PORT, "/mcp/device");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertNull(chain.getRequest());
    }

    @Test
    @DisplayName("错误的 key 返回 401")
    void wrongKey_rejected() throws Exception {
        MockHttpServletRequest request = request(INTERNAL_PORT, "/mcp/device");
        request.addHeader("X-MCP-API-Key", "wrong-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertNull(chain.getRequest());
    }

    @Test
    @DisplayName("X-MCP-API-Key 正确时放行")
    void correctHeaderKey_allowed() throws Exception {
        MockHttpServletRequest request = request(INTERNAL_PORT, "/mcp/device");
        request.addHeader("X-MCP-API-Key", KEY);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertNotNull(chain.getRequest());
    }

    @Test
    @DisplayName("Authorization: Bearer <key> 正确时放行")
    void correctBearerKey_allowed() throws Exception {
        MockHttpServletRequest request = request(INTERNAL_PORT, "/mcp/device");
        request.addHeader("Authorization", "Bearer " + KEY);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertNotNull(chain.getRequest());
    }

    @Test
    @DisplayName("mcp.api-key 未配置时默认拒绝，即使客户端带了任意 key")
    void unconfiguredKey_deniesEverything() throws Exception {
        McpApiKeyFilter unconfiguredFilter = new McpApiKeyFilter("", INTERNAL_PORT);
        MockHttpServletRequest request = request(INTERNAL_PORT, "/mcp/device");
        request.addHeader("X-MCP-API-Key", "anything");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        unconfiguredFilter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertNull(chain.getRequest());
    }

    @Test
    @DisplayName("公网口和非 MCP 路径不受该过滤器影响")
    void otherPortOrPath_passesThrough() throws Exception {
        for (MockHttpServletRequest request : new MockHttpServletRequest[]{
                request(PUBLIC_PORT, "/mcp/device"),
                request(INTERNAL_PORT, "/llm/chat"),
                request(INTERNAL_PORT, "/ws/scene")}) {
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request, new MockHttpServletResponse(), chain);

            assertNotNull(chain.getRequest());
        }
    }

    private static MockHttpServletRequest request(int port, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setLocalPort(port);
        return request;
    }
}
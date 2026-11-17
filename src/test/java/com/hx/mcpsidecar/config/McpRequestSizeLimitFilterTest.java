package com.hx.mcpsidecar.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("MCP 请求体大小限制")
class McpRequestSizeLimitFilterTest {

    private static final int INTERNAL_PORT = 2223;
    private static final int PUBLIC_PORT = 2222;
    private static final long MAX_BYTES = 8;

    private final McpRequestSizeLimitFilter filter =
            new McpRequestSizeLimitFilter(MAX_BYTES, INTERNAL_PORT);

    @Test
    @DisplayName("内网口 Content-Length 超限返回 413，且不进入业务链")
    void internalPort_rejectsOversizedContentLength() throws Exception {
        MockHttpServletRequest request = request(INTERNAL_PORT, "/mcp/device", "0123456789");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(413, response.getStatus());
        assertNull(chain.getRequest());
    }

    @Test
    @DisplayName("内网口正常大小的 MCP 请求放行")
    void internalPort_allowsNormalRequest() throws Exception {
        MockHttpServletRequest request = request(INTERNAL_PORT, "/mcp/device", "12345678");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
    }

    @Test
    @DisplayName("chunked 请求没有 Content-Length 时按实际读取字节数拦截")
    void internalPort_rejectsOversizedChunkedBody() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp/device") {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.setLocalPort(INTERNAL_PORT);
        request.setContent("0123456789".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            byte[] buffer = new byte[32];
            while (req.getInputStream().read(buffer) != -1) {
                // 模拟框架读取完整请求体
            }
        });

        assertEquals(413, response.getStatus());
    }

    @Test
    @DisplayName("公网口和非 MCP 路径不受该过滤器影响")
    void otherPortOrPath_passesThrough() throws Exception {
        for (MockHttpServletRequest request : new MockHttpServletRequest[]{
                request(PUBLIC_PORT, "/mcp/device", "0123456789"),
                request(INTERNAL_PORT, "/llm/chat", "0123456789")}) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request, response, chain);

            assertNotNull(chain.getRequest());
            assertEquals(200, response.getStatus());
        }
    }

    private static MockHttpServletRequest request(int port, String path, String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setLocalPort(port);
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }
}

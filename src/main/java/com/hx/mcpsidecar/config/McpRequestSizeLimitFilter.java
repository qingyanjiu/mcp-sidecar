package com.hx.mcpsidecar.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.InputStream;

/**
 * 限制内网 MCP 端点的请求体大小（SEC-06 的第二道边界）。
 *
 * <p>MCP Java SDK 0.18.4 已修复 CVE-2026-108395，但本服务使用的是
 * {@code WebMvcStreamableServerTransportProvider}，它没有暴露 maxRequestSize 配置项。
 * Tomcat 的 {@code maxPostSize} 只约束 form 表单，不约束 JSON 请求体，因此这里在
 * Servlet 层再加一道上限：</p>
 *
 * <ul>
 *   <li>只作用于内网端口 2223 的 {@code /mcp/**}，不影响 2222 的 /llm 和 /ws；</li>
 *   <li>有 Content-Length 时直接比较，超限返回 413，不读取 body；</li>
 *   <li>chunked 传输没有 Content-Length，用计数的输入流边读边限制，超限抛异常并返回 413。</li>
 * </ul>
 *
 * <p>MCP 的 initialize、tools/call 消息都是小 JSON；1 MiB 已远大于正常工具参数，
 * 调大只会扩大单请求可占用的堆内存。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class McpRequestSizeLimitFilter extends OncePerRequestFilter {

    /** 与 application.yml 的 mcp.max-request-size 保持一致，默认 1 MiB。 */
    static final long DEFAULT_MAX_REQUEST_SIZE = 1024 * 1024;

    private final long maxRequestSize;
    private final int internalPort;

    public McpRequestSizeLimitFilter(
            @Value("${mcp.max-request-size:" + DEFAULT_MAX_REQUEST_SIZE + "}") long maxRequestSize,
            @Value("${mcp.internal-port:2223}") int internalPort) {
        this.maxRequestSize = maxRequestSize;
        this.internalPort = internalPort;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (request.getLocalPort() != internalPort
                || !request.getRequestURI().startsWith("/mcp/")) {
            filterChain.doFilter(request, response);
            return;
        }

        if (request.getContentLengthLong() > maxRequestSize) {
            reject(response);
            return;
        }

        try {
            filterChain.doFilter(new LimitedRequest(request, maxRequestSize), response);
        } catch (RequestTooLargeException e) {
            rejectIfPossible(response);
        } catch (ServletException e) {
            // 下游可能把读取 body 时的异常包成 ServletException，这里追溯 cause 再决定是否 413。
            if (containsTooLarge(e)) {
                rejectIfPossible(response);
            } else {
                throw e;
            }
        }
    }

    private static void rejectIfPossible(HttpServletResponse response) throws IOException {
        if (!response.isCommitted()) {
            response.reset();
            reject(response);
        }
    }

    private static boolean containsTooLarge(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof RequestTooLargeException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static void reject(HttpServletResponse response) throws IOException {
        response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                "MCP request body exceeds configured limit");
    }

    /** 包装请求，替换输入流为带字节上限的版本。 */
    private static final class LimitedRequest extends HttpServletRequestWrapper {

        private final long maxBytes;
        private ServletInputStream inputStream;

        private LimitedRequest(HttpServletRequest request, long maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (inputStream == null) {
                inputStream = new LimitedServletInputStream(super.getInputStream(), maxBytes);
            }
            return inputStream;
        }
    }

    /** 统计实际读取字节数；超过上限时中止，而不是继续把数据读进内存。 */
    private static final class LimitedServletInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private final long maxBytes;
        private long readBytes;

        private LimitedServletInputStream(ServletInputStream delegate, long maxBytes) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value != -1) {
                count(1);
            }
            return value;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            // 最多只向底层多要 1 个字节，用来判断是否超限。
            long remaining = maxBytes - readBytes;
            int toRead = (int) Math.min(len, remaining + 1);
            int count = delegate.read(b, off, toRead);
            if (count > 0) {
                count(count);
            }
            return count;
        }

        @Override
        public int available() throws IOException {
            return delegate.available();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            delegate.setReadListener(readListener);
        }

        private void count(int bytes) {
            readBytes += bytes;
            if (readBytes > maxBytes) {
                throw new RequestTooLargeException();
            }
        }
    }

    /** 请求体超限信号；过滤器捕获后返回 413。 */
    private static final class RequestTooLargeException extends RuntimeException {
    }
}

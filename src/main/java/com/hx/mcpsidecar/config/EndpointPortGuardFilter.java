package com.hx.mcpsidecar.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 按监听端口隔离对外暴露面（{@code server}、{@code local} 均启用）。
 *
 * <ul>
 *   <li>公网端口 2222：只放行 {@code /llm/**}、{@code /ws/**}，其它一律 404</li>
 *   <li>内网端口 2223：只放行 {@code /mcp/**}，其它一律 404</li>
 *   <li>其它端口（actuator 的 9970 等）：不归本过滤器管，直接放行</li>
 * </ul>
 *
 * <p>WebSocket 握手和普通 HTTP 请求走的是同一条 Servlet 过滤链，所以这个过滤器
 * 同时覆盖 {@code /llm/chat}、{@code /ws/scene} 和 MCP 的 POST/GET/DELETE。</p>
 */
@Component
@Profile({"server", "local"})
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class EndpointPortGuardFilter extends OncePerRequestFilter {

    private final int publicPort;
    private final int internalPort;

    public EndpointPortGuardFilter(@Value("${server.port:2222}") int publicPort,
                                   @Value("${mcp.internal-port:2223}") int internalPort) {
        this.publicPort = publicPort;
        this.internalPort = internalPort;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        int localPort = request.getLocalPort();
        String path = request.getRequestURI();

        boolean allowed;
        if (localPort == internalPort) {
            allowed = path.startsWith("/mcp/");
        } else if (localPort == publicPort) {
            allowed = path.startsWith("/llm/") || path.startsWith("/ws/");
        } else {
            // actuator 等独立管理端口，不参与隔离
            allowed = true;
        }

        if (!allowed) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        filterChain.doFilter(request, response);
    }
}

package com.hx.mcpsidecar.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 按监听端口隔离对外暴露面。
 *
 * <p>不绑定 profile：无论用哪个 profile 启动，只要有请求落到未登记的端口，
 * 一律返回 404。缺配置时默认拒绝，而不是默认放行（SEC-08）。</p>
 *
 * <ul>
 *   <li>公网端口 2222：只放行 {@code /llm/**}、{@code /ws/**}，其它一律 404</li>
 *   <li>内网端口 2223：只放行 {@code /mcp/**}，其它一律 404</li>
 *   <li>管理端口 9970：只放行 {@code /actuator/**}</li>
 *   <li>其它端口：默认拒绝</li>
 * </ul>
 *
 * <p>WebSocket 握手和普通 HTTP 请求走的是同一条 Servlet 过滤链，所以这个过滤器
 * 同时覆盖 {@code /llm/chat}、{@code /ws/scene} 和 MCP 的 POST/GET/DELETE。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class EndpointPortGuardFilter extends OncePerRequestFilter {

    private final int publicPort;
    private final int internalPort;
    private final int managementPort;

    public EndpointPortGuardFilter(@Value("${server.port:2222}") int publicPort,
                                   @Value("${mcp.internal-port:2223}") int internalPort,
                                   @Value("${management.server.port:9970}") int managementPort) {
        this.publicPort = publicPort;
        this.internalPort = internalPort;
        this.managementPort = managementPort;
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
        } else if (localPort == managementPort) {
            // 管理端口只暴露 Actuator；即使未来误加其它路由也不会从 9970 出去。
            allowed = path.startsWith("/actuator/");
        } else {
            allowed = false;
        }

        if (!allowed) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        filterChain.doFilter(request, response);
    }
}

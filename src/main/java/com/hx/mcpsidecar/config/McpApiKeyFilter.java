package com.hx.mcpsidecar.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 内网 MCP 端点的应用层鉴权（SEC-08）。
 *
 * <p>端口隔离只解决“谁能连到 2223”，不能解决“连到的人能不能调工具”：
 * 同一 Docker 网络里的任意容器、或 profile 配错时落到 2222 的请求，都绕不过这一层。
 * 因此 {@code /mcp/**} 的每个请求都必须带 API Key：</p>
 *
 * <ul>
 *   <li>推荐 {@code Authorization: Bearer <key>}（标准、MCP 客户端都支持自定义头）；</li>
 *   <li>兼容 {@code X-MCP-API-Key: <key>}，给不便设置 Authorization 的客户端用；</li>
 *   <li>不接受 query 参数，避免 key 进入代理/访问日志。</li>
 * </ul>
 *
 * <p>{@code mcp.api-key} 未配置时按默认拒绝处理：所有 {@code /mcp/**} 请求返回 401。
 * 这是有意为之——缺失安全配置不能退化成“放开”。</p>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class McpApiKeyFilter extends OncePerRequestFilter {

    /** 与 application.yml 的 {@code mcp.api-key} 对应。 */
    private static final String API_KEY_HEADER = "X-MCP-API-Key";
    private static final String BEARER_PREFIX = "Bearer ";

    private final String apiKey;
    private final int internalPort;

    public McpApiKeyFilter(@Value("${mcp.api-key:}") String apiKey,
                           @Value("${mcp.internal-port:2223}") int internalPort) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.internalPort = internalPort;
        if (this.apiKey.isEmpty()) {
            log.error("mcp.api-key 未配置：/mcp/** 的所有请求都会被 401 拒绝（SEC-08）。"
                    + "请在 .env 中设置 MCP_API_KEY 并重启。");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (request.getLocalPort() != internalPort
                || !request.getRequestURI().startsWith("/mcp/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String presented = extractKey(request);
        // apiKey 为空时必须拒绝；不能因为客户端也没带 key 就放行。
        if (apiKey.isEmpty() || presented == null || !constantTimeEquals(apiKey, presented)) {
            reject(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static String extractKey(HttpServletRequest request) {
        String value = request.getHeader(API_KEY_HEADER);
        if (StringUtils.hasText(value)) {
            return value.trim();
        }
        String authorization = request.getHeader("Authorization");
        if (authorization != null
                && authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            String bearer = authorization.substring(BEARER_PREFIX.length()).trim();
            return bearer.isEmpty() ? null : bearer;
        }
        return null;
    }

    /** 定长比较，避免按字符提前返回泄露 key 前缀。 */
    private static boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private static void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader("WWW-Authenticate", "Bearer realm=\"mcp\"");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"UNAUTHORIZED\",\"message\":\"missing or invalid MCP API key\"}");
    }
}
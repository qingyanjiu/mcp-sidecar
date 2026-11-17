package com.hx.mcpsidecar.config.ws;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * WebSocket 跨站来源校验。
 *
 * <p>浏览器发起 WebSocket 时会带 {@code Origin}（页面地址，不是 ws:// 地址）。
 * 默认只允许与 sidecar 同主机的页面；页面部署在其它主机时，通过
 * {@code security.allowed-origins}（逗号分隔，如 {@code http://10.0.0.6:8080}）追加。
 *
 * <p>没有 {@code Origin} 的请求视为服务端客户端（内网 Python Agent）放行，
 * 真正的身份校验仍由 /llm/* 的 token 负责。
 */
@Slf4j
@Component
public class OriginHandshakeInterceptor implements HandshakeInterceptor {

    private final Set<String> allowedOrigins;

    public OriginHandshakeInterceptor(
            @Value("${security.allowed-origins:}") String configuredOrigins) {
        this.allowedOrigins = parseOrigins(configuredOrigins);
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String origin = request.getHeaders().getFirst(HttpHeaders.ORIGIN);
        // 非浏览器客户端（内网 Agent）不会带 Origin，交给后续 token 鉴权
        if (!StringUtils.hasText(origin)) {
            return true;
        }

        String normalizedOrigin;
        try {
            normalizedOrigin = normalize(origin);
        } catch (IllegalArgumentException e) {
            return reject(response, origin, "Origin 格式非法");
        }

        if (allowedOrigins.contains(normalizedOrigin)) {
            return true;
        }

        String requestHost = resolveRequestHost(request);
        if (requestHost != null && requestHost.equalsIgnoreCase(hostOf(normalizedOrigin))) {
            return true;
        }

        return reject(response, origin, "与 sidecar 不同主机且不在白名单");
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }

    private boolean reject(ServerHttpResponse response, String origin, String reason) {
        log.warn("拒绝跨站 WebSocket 握手: origin={}, reason={}", origin, reason);
        response.setStatusCode(HttpStatus.FORBIDDEN);
        return false;
    }

    /** Origin 规范化：scheme://host:port，补默认端口，忽略大小写。 */
    private static String normalize(String origin) {
        URI uri = URI.create(origin.trim());
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || host == null) {
            throw new IllegalArgumentException("origin must be scheme://host[:port]");
        }
        scheme = scheme.toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("unsupported scheme");
        }
        int port = uri.getPort();
        if (port == -1) {
            port = "https".equals(scheme) ? 443 : 80;
        }
        return scheme + "://" + host.toLowerCase(Locale.ROOT) + ":" + port;
    }

    private static Set<String> parseOrigins(String configured) {
        if (!StringUtils.hasText(configured)) {
            return Collections.emptySet();
        }
        Set<String> result = new LinkedHashSet<>();
        Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .forEach(value -> {
                    try {
                        result.add(normalize(value));
                    } catch (IllegalArgumentException e) {
                        log.warn("忽略非法的 security.allowed-origins 配置: {}", value);
                    }
                });
        return result;
    }

    /** 优先用 Host 头，取不到再退回请求 URI 的 host。 */
    private static String resolveRequestHost(ServerHttpRequest request) {
        String host = request.getHeaders().getFirst(HttpHeaders.HOST);
        if (!StringUtils.hasText(host)) {
            return request.getURI().getHost();
        }
        return hostOfAuthority(host);
    }

    private static String hostOf(String normalizedOrigin) {
        return URI.create(normalizedOrigin).getHost();
    }

    /** 从 host[:port] 中取 host，兼容 IPv6 的 [::1]:2222。 */
    private static String hostOfAuthority(String authority) {
        String value = authority.trim();
        if (value.startsWith("[")) {
            int end = value.indexOf(']');
            return end > 0 ? value.substring(1, end) : value;
        }
        int colon = value.indexOf(':');
        return colon > 0 ? value.substring(0, colon) : value;
    }
}

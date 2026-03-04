package com.hx.mcpsidecar.config.ws;

import com.hx.mcpsidecar.service.IAuthService;
import com.hx.mcpsidecar.service.LLMChatLimiter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

/**
 * ws请求握手后拦截器
 */

@Component
@Slf4j
public class AuthHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler wsHandler,
        Map<String, Object> attributes) {

        URI uri = request.getURI();
        // 获取参数
        MultiValueMap<String, String> params =
            UriComponentsBuilder.fromUri(uri).build().getQueryParams();
        // 用户请求时param中的用户id
        String userId = params.getFirst("userId");
        // 用户请求时param中的会话id
        String sessionId = params.getFirst("sessionId");
        String token = request.getHeaders().getFirst("Authorization");
        // 从 query/header 提取变量
        attributes.put("token", token);
        attributes.put("userId", userId);
        attributes.put("sessionId", sessionId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, @Nullable Exception exception) {
    }

}
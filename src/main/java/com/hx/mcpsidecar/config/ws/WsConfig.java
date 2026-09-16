package com.hx.mcpsidecar.config.ws;

import com.hx.mcpsidecar.scene.SceneWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WsConfig implements WebSocketConfigurer {

    @Autowired
    private ProxyWebSocketHandler proxyWebSocketHandler;

    @Autowired
    private AuthHandshakeInterceptor authHandshakeInterceptor;

    @Autowired
    private SceneWebSocketHandler sceneWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(proxyWebSocketHandler, "/llm/chat")
            .setAllowedOrigins("*")
            .addInterceptors(authHandshakeInterceptor);
        registry.addHandler(proxyWebSocketHandler, "/llm/gen_doc")
            .setAllowedOrigins("*")
            .addInterceptors(authHandshakeInterceptor);

        // 场景控制通道：与 /llm/* 不同，这条是 sidecar 自己作为服务端，
        // 且协议规定不鉴权 —— 靠 hello 里的 sessionId 归档（方案 §2.1）。
        registry.addHandler(sceneWebSocketHandler, "/ws/scene")
            .setAllowedOrigins("*");
    }
}
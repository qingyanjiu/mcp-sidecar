package com.hx.mcpsidecar.config.ws;

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

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(proxyWebSocketHandler, "/llm/chat")
            .setAllowedOrigins("*")
            .addInterceptors(authHandshakeInterceptor);
        registry.addHandler(proxyWebSocketHandler, "/llm/gen_doc")
            .setAllowedOrigins("*")
            .addInterceptors(authHandshakeInterceptor);
    }
}
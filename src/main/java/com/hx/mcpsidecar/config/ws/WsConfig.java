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

    @Autowired
    private OriginHandshakeInterceptor originHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Spring 会无条件追加一个内置 OriginHandshakeInterceptor，且它按完整同源（含端口）判断，
        // 会把"同 IP、不同端口"的页面挡掉。这里让内置检查放行，真正的来源校验由
        // OriginHandshakeInterceptor 完成，必须保证它排在拦截器链首位。
        registry.addHandler(proxyWebSocketHandler, "/llm/chat")
            .setAllowedOriginPatterns("http://*:*", "https://*:*")
            .addInterceptors(originHandshakeInterceptor, authHandshakeInterceptor);
        registry.addHandler(proxyWebSocketHandler, "/llm/gen_doc")
            .setAllowedOriginPatterns("http://*:*", "https://*:*")
            .addInterceptors(originHandshakeInterceptor, authHandshakeInterceptor);

        // 场景控制通道：sidecar 自己作为服务端。握手先由 AuthHandshakeInterceptor
        // 提取 token/userId（浏览器 WS 不能设 Header，走 ?token= 回退），
        // SceneWebSocketHandler 在 hello 时调用平台校验 token 与 userId。
        // Origin 校验仍然保留，避免任意网站页面跨站建立场景连接。
        registry.addHandler(sceneWebSocketHandler, "/ws/scene")
            .setAllowedOriginPatterns("http://*:*", "https://*:*")
            .addInterceptors(originHandshakeInterceptor, authHandshakeInterceptor);
    }
}
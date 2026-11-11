package com.hx.mcpsidecar.config.ws;

import com.hx.mcpsidecar.service.IAuthService;
import com.hx.mcpsidecar.service.LLMChatLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.socket.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.net.URI;

@Slf4j
@Component
public class ProxyWebSocketHandler implements WebSocketHandler {

    @Autowired
    private LLMConfiguration llmConfiguration;

    @Autowired
    private IAuthService authService;

    @Autowired
    private LLMChatLimiter llmChatLimiter;

    @Override
    public void afterConnectionEstablished(WebSocketSession client) throws Exception {
        String token = (String) client.getAttributes().get("token");
        String userId = (String) client.getAttributes().get("userId");
        String sessionId = (String) client.getAttributes().get("sessionId");

        // 先判空再处理 token，否则未带 Authorization 头的握手会在这里 NPE，
        // 客户端也收不到下面的 UNAUTHORIZED 提示。
        if (token == null || token.isBlank()) {
            log.warn("ws握手未携带token(Authorization头和query参数均为空)，系统拦截此对话请求, uri={}", client.getUri());
            client.sendMessage(new TextMessage(
                "{\"error\":\"UNAUTHORIZED\",\"message\":\"未检测到token或者userId信息!\"}"
            ));
            client.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }
        if (userId == null || userId.isBlank()) {
            log.warn("ws握手未携带userId(query参数为空)，系统拦截此对话请求, uri={}", client.getUri());
            client.sendMessage(new TextMessage(
                "{\"error\":\"UNAUTHORIZED\",\"message\":\"未检测到token或者userId信息!\"}"
            ));
            client.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        if (token.startsWith("Bearer ")) {
            // 去Bearer头
            token = token.substring(7);
        }
        // 1.验证用户登录
        boolean validSession = authService.validateToken(token, userId);
        if (!validSession) {
            log.info("不可用的token，系统拦截此对话请求, userId={}", userId);
            client.sendMessage(new TextMessage(
                "{\"error\":\"UNAUTHORIZED\",\"message\":\"用户未登录!\"}"
            ));
            client.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }
        // 2.并发限制
        boolean limitCheck = llmChatLimiter.tryToChat(userId);
        if (!limitCheck) {
            log.info("当前使用人数多，系统拦截此对话请求, userId={}", userId);
            client.sendMessage(new TextMessage(
                "{\"error\":\"TOO_MANY_REQUESTS\",\"message\":\"当前用户数太多，请稍后再试!\"}"
            ));
            client.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        String wsUrl = "";
        String path = client.getUri().getPath();
        // 根据前缀判断代理哪个ws请求
        if (path.startsWith("/llm/chat")) {
            wsUrl = llmConfiguration.getWsPath().get("chat");
        } else if (path.startsWith("/llm/gen_doc")) {
            wsUrl = llmConfiguration.getWsPath().get("gen_doc");
        }
        wsUrl = String.format(wsUrl, userId, sessionId);
        URI uri = URI.create(wsUrl);

        // 连接到后端 Python WS
        StandardWebSocketClient wsClient = new StandardWebSocketClient();
        ListenableFuture<WebSocketSession> future =
            wsClient.doHandshake(new BackendHandler(client, uri), null, uri);
        WebSocketSession backendSession = future.get();

        // 将后端会话放到属性里，便于转发
        client.getAttributes().put("backend", backendSession);
    }

    @Override
    public void handleMessage(WebSocketSession client, WebSocketMessage<?> message) throws Exception {
        WebSocketSession backend = (WebSocketSession) client.getAttributes().get("backend");
        if (backend == null || !backend.isOpen()) {
            log.warn("后端ws会话不可用，忽略客户端消息");
            return;
        }
        // 直接转发客户端消息到 Python 后端
        backend.sendMessage(message);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession client, CloseStatus status) throws Exception {
        WebSocketSession backend = (WebSocketSession) client.getAttributes().get("backend");
        if (backend != null && backend.isOpen()) {
            backend.close();
        }
        String userId = (String) client.getAttributes().get("userId");
        // 握手阶段就被拦截的连接没有 userId，也没有占用过信号量，不能在这里 NPE
        if (userId == null || userId.isBlank()) {
            log.info("ws连接关闭，未携带userId，无需释放信号量");
            return;
        }
        // 释放信号量限流许可
        llmChatLimiter.completeChat(userId);
        log.info("ws连接关闭，自动释放信号量, userId - {}", userId);
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {

    }

}

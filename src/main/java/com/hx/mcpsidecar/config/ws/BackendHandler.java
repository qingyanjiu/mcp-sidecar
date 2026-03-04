package com.hx.mcpsidecar.config.ws;

import org.springframework.web.socket.*;

/**
 * 连接python端ws接口的连接类
 */
public class BackendHandler implements WebSocketHandler {

    private final WebSocketSession clientSession;

    public BackendHandler(WebSocketSession clientSession) {
        this.clientSession = clientSession;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession backendSession) {
        System.out.println("Backend WebSocket connected: " + backendSession.getUri());
    }

    @Override
    public void handleMessage(WebSocketSession backendSession, WebSocketMessage<?> message)
        throws Exception {
        // 1. 接收到 Python 后端发来的消息
        String payload = ((TextMessage) message).getPayload();

        // 2. 转发给前端的 WebSocket
        if (clientSession.isOpen()) {
            clientSession.sendMessage(new TextMessage(payload));
        }
    }

    @Override
    public void handleTransportError(WebSocketSession backendSession, Throwable exception)
        throws Exception {
        backendSession.close();
    }

    @Override
    public void afterConnectionClosed(WebSocketSession backendSession, CloseStatus status)
        throws Exception {
        // 通知前端客户端后端连接关闭
        if (clientSession.isOpen()) {
            clientSession.close();
        }
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
}
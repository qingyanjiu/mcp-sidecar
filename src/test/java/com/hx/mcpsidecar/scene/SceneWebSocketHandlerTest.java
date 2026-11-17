package com.hx.mcpsidecar.scene;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hx.mcpsidecar.scene.SceneSessionRegistry.BindResult;
import com.hx.mcpsidecar.scene.SceneSessionRegistry.BindStatus;
import com.hx.mcpsidecar.service.IAuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("场景 WS 鉴权")
class SceneWebSocketHandlerTest {

    private static final String WS_ID = "ws-1";

    private final SceneSessionRegistry registry = mock(SceneSessionRegistry.class);
    private final SceneDispatcher dispatcher = mock(SceneDispatcher.class);
    private final IAuthService authService = mock(IAuthService.class);
    private final SceneWebSocketHandler handler =
            new SceneWebSocketHandler(registry, dispatcher, new ObjectMapper(), authService);

    @Test
    @DisplayName("hello 未携带 token 时拒绝连接")
    void hello_withoutToken_isRejected() throws Exception {
        WebSocketSession socket = socket(new HashMap<>());

        handler.handleMessage(socket, hello("s-1", "u-1"));

        verify(socket).close(CloseStatus.NOT_ACCEPTABLE);
        verifyNoInteractions(registry);
        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("token 校验失败时拒绝连接")
    void hello_withInvalidToken_isRejected() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("token", "bad-token");
        attributes.put("userId", "u-1");
        when(authService.validateToken("bad-token", "u-1")).thenReturn(false);
        WebSocketSession socket = socket(attributes);

        handler.handleMessage(socket, hello("s-1", "u-1"));

        verify(authService).validateToken("bad-token", "u-1");
        verify(socket).close(CloseStatus.NOT_ACCEPTABLE);
        verifyNoInteractions(registry);
    }

    @Test
    @DisplayName("query userId 与 hello userId 不一致时拒绝连接")
    void hello_withMismatchedUserId_isRejected() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("token", "good-token");
        attributes.put("userId", "u-1");
        WebSocketSession socket = socket(attributes);

        handler.handleMessage(socket, hello("s-1", "u-2"));

        verifyNoInteractions(authService);
        verify(socket).close(CloseStatus.POLICY_VIOLATION);
        verifyNoInteractions(registry);
    }

    @Test
    @DisplayName("token 有效且 userId 一致时归档会话")
    void hello_withValidToken_bindsSession() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("token", "Bearer good-token");
        attributes.put("userId", "u-1");
        when(authService.validateToken("good-token", "u-1")).thenReturn(true);
        SceneSession session = new SceneSession("s-1");
        WebSocketSession socket = socket(attributes);
        when(registry.bind("s-1", "u-1", socket)).thenReturn(
                new BindResult(session, null, BindStatus.BOUND));

        handler.handleMessage(socket, hello("s-1", "u-1"));

        verify(registry).bind("s-1", "u-1", socket);
        verify(dispatcher).redispatchPending(session);
        verify(socket, never()).close(any(CloseStatus.class));
    }

    @Test
    @DisplayName("sessionId 已属于其他用户时拒绝连接")
    void hello_withSessionOwnedByAnotherUser_isRejected() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("token", "good-token");
        attributes.put("userId", "u-1");
        when(authService.validateToken("good-token", "u-1")).thenReturn(true);
        WebSocketSession socket = socket(attributes);
        when(registry.bind("s-1", "u-1", socket)).thenReturn(
                new BindResult(new SceneSession("s-1"), null, BindStatus.USER_MISMATCH));

        handler.handleMessage(socket, hello("s-1", "u-1"));

        verify(socket).close(CloseStatus.POLICY_VIOLATION);
        verifyNoInteractions(dispatcher);
    }

    @Test
    @DisplayName("会话数达到上限时拒绝新会话")
    void hello_whenCapacityExceeded_isRejected() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("token", "good-token");
        attributes.put("userId", "u-1");
        when(authService.validateToken("good-token", "u-1")).thenReturn(true);
        WebSocketSession socket = socket(attributes);
        when(registry.bind("s-1", "u-1", socket)).thenReturn(
                new BindResult(null, null, BindStatus.CAPACITY_EXCEEDED));

        handler.handleMessage(socket, hello("s-1", "u-1"));

        verify(socket).close(new CloseStatus(1013, "session capacity exceeded"));
        verifyNoInteractions(dispatcher);
    }

    @Test
    @DisplayName("userId 缺失时拒绝连接")
    void hello_withoutUserId_isRejected() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("token", "good-token");
        WebSocketSession socket = socket(attributes);

        handler.handleMessage(socket, new TextMessage("{\"type\":\"hello\",\"sessionId\":\"s-1\"}"));

        verify(socket).close(CloseStatus.NOT_ACCEPTABLE);
        verifyNoInteractions(authService);
        verifyNoInteractions(registry);
    }

    @Test
    @DisplayName("sessionId 过长时拒绝连接")
    void hello_withOversizedSessionId_isRejected() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("token", "good-token");
        attributes.put("userId", "u-1");
        when(authService.validateToken("good-token", "u-1")).thenReturn(true);
        WebSocketSession socket = socket(attributes);

        handler.handleMessage(socket, hello("s".repeat(129), "u-1"));

        verify(socket).close(CloseStatus.POLICY_VIOLATION);
        verifyNoInteractions(registry);
    }

    private static TextMessage hello(String sessionId, String userId) {
        return new TextMessage("{\"type\":\"hello\",\"sessionId\":\"" + sessionId
                + "\",\"userId\":\"" + userId + "\"}");
    }

    private static WebSocketSession socket(Map<String, Object> attributes) throws Exception {
        WebSocketSession socket = mock(WebSocketSession.class);
        when(socket.getId()).thenReturn(WS_ID);
        when(socket.getAttributes()).thenReturn(attributes);
        when(socket.isOpen()).thenReturn(true);
        return socket;
    }
}

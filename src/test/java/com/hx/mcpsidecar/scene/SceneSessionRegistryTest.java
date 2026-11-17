package com.hx.mcpsidecar.scene;

import com.hx.mcpsidecar.scene.SceneSessionRegistry.BindResult;
import com.hx.mcpsidecar.scene.SceneSessionRegistry.BindStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("场景会话注册表")
class SceneSessionRegistryTest {

    private final SceneProperties properties = new SceneProperties();

    @Test
    @DisplayName("同一用户重连复用会话并顶掉旧连接")
    void sameUserReconnect_evictsOldSocket() {
        SceneSessionRegistry registry = new SceneSessionRegistry(properties);
        WebSocketSession first = socket("ws-1");
        WebSocketSession second = socket("ws-2");

        BindResult bound = registry.bind("s-1", "u-1", first);
        BindResult reconnect = registry.bind("s-1", "u-1", second);

        assertEquals(BindStatus.BOUND, bound.status());
        assertEquals(BindStatus.BOUND, reconnect.status());
        assertSame(first, reconnect.evicted());
        assertEquals(1, registry.connectedCount());
    }

    @Test
    @DisplayName("其他用户不能顶替已占用的 sessionId")
    void differentUser_cannotTakeOverSession() {
        SceneSessionRegistry registry = new SceneSessionRegistry(properties);
        registry.bind("s-1", "u-1", socket("ws-1"));

        BindResult result = registry.bind("s-1", "u-2", socket("ws-2"));

        assertEquals(BindStatus.USER_MISMATCH, result.status());
        assertEquals("u-1", registry.findConnected("s-1").getUserId());
    }

    @Test
    @DisplayName("会话数达到上限后拒绝新 sessionId，已有 sessionId 仍可重连")
    void maxSessions_limitsNewSessionsButNotReconnects() {
        properties.setMaxSessions(1);
        SceneSessionRegistry registry = new SceneSessionRegistry(properties);
        registry.bind("s-1", "u-1", socket("ws-1"));

        assertEquals(BindStatus.CAPACITY_EXCEEDED,
                registry.bind("s-2", "u-2", socket("ws-2")).status());
        assertEquals(BindStatus.BOUND,
                registry.bind("s-1", "u-1", socket("ws-3")).status());
    }

    private static WebSocketSession socket(String id) {
        WebSocketSession socket = mock(WebSocketSession.class);
        when(socket.getId()).thenReturn(id);
        when(socket.getAttributes()).thenReturn(Map.of());
        when(socket.isOpen()).thenReturn(true);
        return socket;
    }
}

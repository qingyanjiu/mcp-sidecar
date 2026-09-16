package com.hx.mcpsidecar.scene;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hx.mcpsidecar.scene.SceneSession.PendingAction;
import com.hx.mcpsidecar.scene.SceneSessionRegistry.BindResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * {@code /ws/scene} 端点的处理器（方案 §2.3）。
 *
 * <p>入站帧用 {@code type} 字段区分，出站帧用 {@code event}/{@code data} —— 这个不对称是协议
 * 原文如此，照抄。</p>
 *
 * <p>鉴权：无（方案 §2.1）。靠 {@code hello} 里的 {@code sessionId} 归档。</p>
 */
@Slf4j
@Component
public class SceneWebSocketHandler extends TextWebSocketHandler {

    /** socket 属性键：这条连接认领的 sessionId。断开时靠它反查会话。 */
    private static final String ATTR_SESSION_ID = "sceneSessionId";

    private static final String TYPE_HELLO = "hello";
    private static final String TYPE_SCENE_STATE = "scene_state";
    private static final String TYPE_ACTION_RESULT = "action_result";
    private static final String TYPE_ACTION_CONFIRM = "action_confirm";

    private final SceneSessionRegistry registry;
    private final SceneDispatcher dispatcher;
    private final ObjectMapper objectMapper;

    public SceneWebSocketHandler(SceneSessionRegistry registry, SceneDispatcher dispatcher,
                                 ObjectMapper objectMapper) {
        this.registry = registry;
        this.dispatcher = dispatcher;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession socket) {
        // 这里不归档：sessionId 要等 hello 才知道（方案 §2.3 ①）。
        log.info("场景 WS 已连接，等待 hello: wsId={}", socket.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession socket, TextMessage message) {
        JsonNode frame;
        try {
            frame = objectMapper.readTree(message.getPayload());
        } catch (Exception e) {
            log.warn("场景 WS 收到无法解析的帧: wsId={}, payload={}", socket.getId(), message.getPayload());
            return;
        }

        String type = frame.path("type").asText("");
        switch (type) {
            case TYPE_HELLO -> onHello(socket, frame);
            case TYPE_SCENE_STATE -> onSceneState(socket, frame, message.getPayload());
            case TYPE_ACTION_RESULT -> onActionResult(socket, frame);
            case TYPE_ACTION_CONFIRM -> onActionConfirm(socket, frame);
            default -> log.warn("场景 WS 收到未知帧类型: wsId={}, type={}", socket.getId(), type);
        }
    }

    /** ① 连接归档。收到 hello 之前不接受任何其他帧。 */
    private void onHello(WebSocketSession socket, JsonNode frame) {
        String sessionId = textOf(frame, "sessionId");
        String userId = textOf(frame, "userId");

        if (sessionId == null || sessionId.isBlank()) {
            log.warn("hello 缺少 sessionId，关闭连接: wsId={}", socket.getId());
            closeQuietly(socket, CloseStatus.POLICY_VIOLATION);
            return;
        }

        BindResult result = registry.bind(sessionId, userId, socket);
        socket.getAttributes().put(ATTR_SESSION_ID, sessionId);

        WebSocketSession evicted = result.evicted();
        if (evicted != null && evicted.isOpen()) {
            // 同一 sessionId 新连接顶掉旧连接：旧页面可能是没关干净的标签页，
            // 让它继续收动作帧会导致同一个动作被执行两次。
            log.info("关闭被顶替的场景连接: sessionId={}, oldWsId={}", sessionId, evicted.getId());
            closeQuietly(evicted, CloseStatus.NORMAL);
        }

        // 刷新页面 / 断网恢复后，把在途动作重发给新连接，否则那些工具调用只能白等到超时。
        dispatcher.redispatchPending(result.session());
    }

    /** ② 场景状态快照。覆盖式缓存，不解析不校验（方案 §2.3）。 */
    private void onSceneState(WebSocketSession socket, JsonNode frame, String raw) {
        SceneSession session = boundSession(socket);
        if (session == null) {
            return;
        }
        session.updateSceneState(raw);
        log.debug("场景状态已更新: sessionId={}, ready={}, floor={}",
            session.getSessionId(), frame.path("ready").asBoolean(false), frame.path("floor").asText("?"));
    }

    /** ③ 动作回执，兑现挂起的工具调用。 */
    private void onActionResult(WebSocketSession socket, JsonNode frame) {
        SceneSession session = boundSession(socket);
        if (session == null) {
            return;
        }
        String actionId = textOf(frame, "actionId");
        if (actionId == null) {
            log.warn("action_result 缺少 actionId: sessionId={}", session.getSessionId());
            return;
        }

        PendingAction pending = session.take(actionId);
        if (pending == null) {
            // 超时后才回来的迟到回执，或者重复回执。丢掉即可。
            log.debug("收到无对应挂起项的回执（可能已超时）: sessionId={}, actionId={}",
                session.getSessionId(), actionId);
            return;
        }

        String state = textOf(frame, "state");
        String rawError = textOf(frame, "error");
        SceneOutcome outcome = SceneOutcome.REJECTED.equals(state)
            // rejected 必须转成可读错误，BAD_PARAM:<key> 的 key 原样透给模型（方案 §2.5 第 4 点）。
            ? SceneOutcome.rejected(SceneDispatcher.describeReject(rawError))
            : new SceneOutcome(state == null ? SceneOutcome.DISPATCHED : state, null, null);

        log.info("动作回执: sessionId={}, actionId={}, name={}, state={}, durationMs={}",
            session.getSessionId(), actionId, pending.getName(), outcome.state(),
            frame.path("durationMs").asText("?"));
        pending.complete(outcome);
    }

    /**
     * ④ 确认回执。方案 §2.4 / §六 Phase 4 才需要，前端当前不产生这条，
     * sidecar 侧也还没实现 {@code event:"confirm"} 的发起路径，所以先只记日志。
     */
    private void onActionConfirm(WebSocketSession socket, JsonNode frame) {
        SceneSession session = boundSession(socket);
        log.info("收到 action_confirm（Phase 4 尚未启用）: sessionId={}, actionId={}, approved={}",
            session == null ? "?" : session.getSessionId(),
            textOf(frame, "actionId"), frame.path("approved").asBoolean(false));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession socket, CloseStatus status) {
        Object sessionId = socket.getAttributes().get(ATTR_SESSION_ID);
        if (sessionId == null) {
            log.info("场景 WS 在 hello 之前就断开了: wsId={}, status={}", socket.getId(), status);
            return;
        }
        registry.unbind(sessionId.toString(), socket);
    }

    @Override
    public void handleTransportError(WebSocketSession socket, Throwable exception) {
        log.warn("场景 WS 传输异常: wsId={}, reason={}", socket.getId(), exception.getMessage());
        closeQuietly(socket, CloseStatus.SERVER_ERROR);
    }

    /** 取这条连接已归档的会话；未收到 hello 时返回 null。 */
    private SceneSession boundSession(WebSocketSession socket) {
        Object sessionId = socket.getAttributes().get(ATTR_SESSION_ID);
        if (sessionId == null) {
            log.warn("尚未收到 hello 就收到其他帧，已忽略: wsId={}", socket.getId());
            return null;
        }
        SceneSession session = registry.findConnected(sessionId.toString());
        if (session == null) {
            log.warn("帧对应的会话已不在注册表中: sessionId={}", sessionId);
        }
        return session;
    }

    private static String textOf(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static void closeQuietly(WebSocketSession socket, CloseStatus status) {
        try {
            socket.close(status);
        } catch (Exception e) {
            log.debug("关闭场景 WS 失败: wsId={}", socket.getId(), e);
        }
    }
}
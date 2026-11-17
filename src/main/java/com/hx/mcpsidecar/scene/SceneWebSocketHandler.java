package com.hx.mcpsidecar.scene;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hx.mcpsidecar.scene.SceneSession.PendingAction;
import com.hx.mcpsidecar.scene.SceneSessionRegistry.BindResult;
import com.hx.mcpsidecar.scene.SceneSessionRegistry.BindStatus;
import com.hx.mcpsidecar.service.IAuthService;
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
 * <p>鉴权：握手时通过 {@code Authorization} 头或 {@code ?token=} 携带平台 token，
 * {@code hello} 时调用平台校验 token 与 userId 是否匹配；校验通过后 sessionId 归该 userId 所有。
 * 浏览器原生 WebSocket 不能设置自定义 Header，因此 query 参数是主要方式。</p>
 */
@Slf4j
@Component
public class SceneWebSocketHandler extends TextWebSocketHandler {

    /** socket 属性键：这条连接认领的 sessionId。断开时靠它反查会话。 */
    private static final String ATTR_SESSION_ID = "sceneSessionId";

    /** 握手拦截器写入的属性键，与 AuthHandshakeInterceptor 保持一致。 */
    private static final String ATTR_TOKEN = "token";
    private static final String ATTR_USER_ID = "userId";

    private static final String TYPE_HELLO = "hello";
    private static final String TYPE_SCENE_STATE = "scene_state";
    private static final String TYPE_ACTION_RESULT = "action_result";
    private static final String TYPE_ACTION_CONFIRM = "action_confirm";

    /** RFC 6455 的 1013（Try Again Later）；Spring 的 CloseStatus 没有内置常量。 */
    private static final CloseStatus TRY_AGAIN_LATER = new CloseStatus(1013, "session capacity exceeded");

    /** sessionId 会作为 Map key 长期保留，限制长度避免超长字符串占用内存。 */
    private static final int MAX_SESSION_ID_LENGTH = 128;

    private final SceneSessionRegistry registry;
    private final SceneDispatcher dispatcher;
    private final ObjectMapper objectMapper;
    private final IAuthService authService;

    public SceneWebSocketHandler(SceneSessionRegistry registry, SceneDispatcher dispatcher,
                                 ObjectMapper objectMapper, IAuthService authService) {
        this.registry = registry;
        this.dispatcher = dispatcher;
        this.objectMapper = objectMapper;
        this.authService = authService;
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
            // 原始帧可能包含场景状态/业务数据，不落日志；只记录长度和异常类型。
            log.warn("场景 WS 收到无法解析的帧: wsId={}, payloadLength={}, error={}",
                    socket.getId(), message.getPayload().length(), e.getClass().getSimpleName());
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
        String userId = authenticate(socket, frame);
        if (userId == null) {
            // authenticate 已经记录原因并关闭连接。
            return;
        }

        if (sessionId == null || sessionId.isBlank()) {
            log.warn("hello 缺少 sessionId，关闭连接: wsId={}", socket.getId());
            closeQuietly(socket, CloseStatus.POLICY_VIOLATION);
            return;
        }
        if (sessionId.length() > MAX_SESSION_ID_LENGTH) {
            log.warn("hello 的 sessionId 过长，关闭连接: wsId={}, length={}",
                    socket.getId(), sessionId.length());
            closeQuietly(socket, CloseStatus.POLICY_VIOLATION);
            return;
        }

        BindResult result = registry.bind(sessionId, userId, socket);
        if (result.status() == BindStatus.USER_MISMATCH) {
            log.warn("hello 的 sessionId 属于其他用户，关闭连接: wsId={}, sessionId={}", socket.getId(), sessionId);
            closeQuietly(socket, CloseStatus.POLICY_VIOLATION);
            return;
        }
        if (result.status() == BindStatus.CAPACITY_EXCEEDED) {
            log.warn("场景会话数已达上限，关闭连接: wsId={}", socket.getId());
            closeQuietly(socket, TRY_AGAIN_LATER);
            return;
        }

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

    /**
     * 校验握手携带的 token，并确定本次连接的可信 userId。
     *
     * <p>userId 可以放在 query（{@code ?userId=}）或 hello 帧里，但两处都填时必须一致；
     * 无论从哪来，最终都以平台返回的 token/userId 匹配结果为准。校验失败会直接关闭连接。</p>
     *
     * @return 校验通过的 userId；失败返回 {@code null}（连接已关闭）
     */
    private String authenticate(WebSocketSession socket, JsonNode frame) {
        String token = attribute(socket, ATTR_TOKEN);
        String handshakeUserId = attribute(socket, ATTR_USER_ID);
        String helloUserId = textOf(frame, "userId");

        if (token == null || token.isBlank()) {
            log.warn("场景 WS 握手未携带 token，拒绝连接: wsId={}", socket.getId());
            closeQuietly(socket, CloseStatus.NOT_ACCEPTABLE);
            return null;
        }

        String userId;
        if (handshakeUserId != null && !handshakeUserId.isBlank()) {
            if (helloUserId != null && !helloUserId.isBlank()
                    && !handshakeUserId.equals(helloUserId)) {
                log.warn("场景 WS 的 query userId 与 hello userId 不一致，拒绝连接: wsId={}", socket.getId());
                closeQuietly(socket, CloseStatus.POLICY_VIOLATION);
                return null;
            }
            userId = handshakeUserId;
        } else if (helloUserId != null && !helloUserId.isBlank()) {
            userId = helloUserId;
        } else {
            log.warn("场景 WS 未携带 userId，拒绝连接: wsId={}", socket.getId());
            closeQuietly(socket, CloseStatus.NOT_ACCEPTABLE);
            return null;
        }

        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (token.isBlank()) {
            log.warn("场景 WS 的 token 为空，拒绝连接: wsId={}", socket.getId());
            closeQuietly(socket, CloseStatus.NOT_ACCEPTABLE);
            return null;
        }

        try {
            if (!authService.validateToken(token, userId)) {
                log.warn("场景 WS token 校验未通过，拒绝连接: wsId={}, userId={}", socket.getId(), userId);
                closeQuietly(socket, CloseStatus.NOT_ACCEPTABLE);
                return null;
            }
        } catch (Exception e) {
            // 平台不可用时不把细节回给客户端，也不让异常冒泡成 500 帧。
            log.warn("场景 WS token 校验异常，拒绝连接: wsId={}, error={}",
                    socket.getId(), e.getClass().getSimpleName());
            closeQuietly(socket, CloseStatus.SERVER_ERROR);
            return null;
        }

        socket.getAttributes().put(ATTR_USER_ID, userId);
        return userId;
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

    private static String attribute(WebSocketSession socket, String name) {
        Object value = socket.getAttributes().get(name);
        return value == null ? null : value.toString();
    }

    private static void closeQuietly(WebSocketSession socket, CloseStatus status) {
        try {
            socket.close(status);
        } catch (Exception e) {
            log.debug("关闭场景 WS 失败: wsId={}", socket.getId(), e);
        }
    }
}
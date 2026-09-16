package com.hx.mcpsidecar.scene;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 一个 {@code sessionId} 对应的场景会话。
 *
 * <p><b>挂起项与状态快照都挂在 sessionId 上，不挂在 socket 实例上</b>（方案 §2.3）。
 * 页面刷新 / 断网恢复时新连接覆盖旧的 socket，{@link #pending} 原样保留，
 * 否则刷新一次页面会让所有在途工具调用白等到超时。</p>
 */
@Slf4j
public class SceneSession {

    private final String sessionId;

    /** 当前持有的 socket。重连时整体替换。 */
    private volatile WebSocketSession socket;

    private volatile String userId;

    /** 最近一次 {@code scene_state} 的原始 JSON，覆盖式缓存，不解析不校验（方案 §2.3）。 */
    private volatile String sceneStateJson;

    private volatile long sceneStateAt;

    /** actionId → 在途动作。挂在会话上，跨 socket 存活。 */
    private final Map<String, PendingAction> pending = new ConcurrentHashMap<>();

    /** {@link WebSocketSession#sendMessage} 不是线程安全的，多个工具并发下发时要串行。 */
    private final Object sendLock = new Object();

    private volatile long lastSeenAt = System.currentTimeMillis();

    public SceneSession(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public boolean isConnected() {
        WebSocketSession current = socket;
        return current != null && current.isOpen();
    }

    /**
     * 绑定（或重连替换）socket。
     *
     * @return 被顶替掉的旧 socket；没有则返回 {@code null}。调用方负责把它关掉。
     */
    public WebSocketSession bind(String userId, WebSocketSession newSocket) {
        WebSocketSession old = this.socket;
        this.userId = userId;
        this.socket = newSocket;
        this.lastSeenAt = System.currentTimeMillis();
        return old == newSocket ? null : old;
    }

    /**
     * 解绑 socket。<b>只有当前 socket 才解绑</b> —— 旧 socket 因被顶替而异步关闭时，
     * 不能把新连接一起清掉。
     *
     * @return 是否真的解绑了
     */
    public boolean unbind(WebSocketSession closing) {
        if (this.socket != closing) {
            return false;
        }
        this.socket = null;
        this.lastSeenAt = System.currentTimeMillis();
        return true;
    }

    /** 发一帧文本。socket 不可用或发送失败时抛 {@link IOException}，由调用方决定怎么收场。 */
    public void send(String json) throws IOException {
        WebSocketSession current = socket;
        if (current == null || !current.isOpen()) {
            throw new IOException("场景页面连接已断开");
        }
        synchronized (sendLock) {
            current.sendMessage(new TextMessage(json));
        }
    }

    // ---------- 场景状态快照 ----------

    public void updateSceneState(String json) {
        this.sceneStateJson = json;
        this.sceneStateAt = System.currentTimeMillis();
    }

    public String getSceneStateJson() {
        return sceneStateJson;
    }

    public long getSceneStateAt() {
        return sceneStateAt;
    }

    // ---------- 挂起 / 唤醒 ----------

    public void park(String actionId, PendingAction action) {
        pending.put(actionId, action);
        this.lastSeenAt = System.currentTimeMillis();
    }

    public void unpark(String actionId) {
        pending.remove(actionId);
        this.lastSeenAt = System.currentTimeMillis();
    }

    public PendingAction take(String actionId) {
        PendingAction action = pending.get(actionId);
        if (action != null) {
            this.lastSeenAt = System.currentTimeMillis();
        }
        return action;
    }

    /** 当前在途动作的快照，用于重连后重发。 */
    public List<PendingAction> pendingSnapshot() {
        return new ArrayList<>(pending.values());
    }

    public boolean hasPending() {
        return !pending.isEmpty();
    }

    public long getLastSeenAt() {
        return lastSeenAt;
    }

    /**
     * 一次在途的工具调用。{@link #future} 由 {@code action_result} 兑现，
     * 超时由 {@link SceneDispatcher} 负责。
     */
    public static final class PendingAction {

        private final String actionId;
        private final String name;
        private final Map<String, Object> params;
        private final CompletableFuture<SceneOutcome> future = new CompletableFuture<>();

        public PendingAction(String actionId, String name, Map<String, Object> params) {
            this.actionId = actionId;
            this.name = name;
            this.params = params;
        }

        public String getActionId() {
            return actionId;
        }

        public String getName() {
            return name;
        }

        public Map<String, Object> getParams() {
            return params;
        }

        public CompletableFuture<SceneOutcome> getFuture() {
            return future;
        }

        /** 页面回执到达。重复回执（含重连后的迟到回执）直接忽略。 */
        public void complete(SceneOutcome outcome) {
            future.complete(outcome);
        }
    }
}
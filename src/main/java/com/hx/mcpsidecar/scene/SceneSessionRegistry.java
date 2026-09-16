package com.hx.mcpsidecar.scene;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code sessionId → SceneSession} 映射。
 *
 * <p>路由键只有 {@code sessionId} 一个（方案 §2.3）。同一个 sessionId 重连时新连接覆盖旧的，
 * 会话对象本身复用，所以挂起项与场景状态快照天然跟着走。</p>
 */
@Slf4j
@Component
public class SceneSessionRegistry {

    private final Map<String, SceneSession> sessions = new ConcurrentHashMap<>();

    private final SceneProperties properties;

    public SceneSessionRegistry(SceneProperties properties) {
        this.properties = properties;
    }

    /**
     * 归档一条 {@code hello}。
     *
     * @return {@link BindResult#session()} 是复用的会话对象；
     *         {@link BindResult#evicted()} 是被顶替的旧 socket，调用方负责关闭它
     */
    public BindResult bind(String sessionId, String userId, WebSocketSession socket) {
        SceneSession session = sessions.computeIfAbsent(sessionId, SceneSession::new);
        WebSocketSession old = session.bind(userId, socket);
        if (old != null) {
            log.info("场景会话重连，顶替旧连接: ws-session-id={}, user-id={}", sessionId, userId);
        } else {
            log.info("场景会话接入: ws-session-id={}, user-id={}", sessionId, userId);
        }
        return new BindResult(session, old);
    }

    /**
     * 解绑。旧 socket 被顶替后才异步关闭时，这里会因为身份不符而什么都不做。
     */
    public void unbind(String sessionId, WebSocketSession socket) {
        SceneSession session = sessions.get(sessionId);
        if (session == null) {
            return;
        }
        if (session.unbind(socket)) {
            log.info("场景会话断开: sessionId={}，在途动作 {} 个（保留等待重连）",
                sessionId, session.pendingSnapshot().size());
        }
    }

    /**
     * 按 sessionId 取一条<b>当前可用</b>的会话。未连接的一律当作不存在 ——
     * 方案 §2.5 要求查不到连接就立刻失败，不能挂起等超时。
     */
    public SceneSession findConnected(String sessionId) {
        SceneSession session = sessions.get(sessionId);
        return session != null && session.isConnected() ? session : null;
    }

    /**
     * 解析工具调用该发给谁。
     *
     * @param headerSessionId 从 {@code X-Session-Id} header 取到的会话 id，可能为 null
     * @return 命中的会话；返回 null 表示"没有可用连接"，调用方应立刻报错而不是挂起
     */
    public SceneSession resolve(String headerSessionId) {
        if (headerSessionId != null && !headerSessionId.isBlank()) {
            SceneSession session = findConnected(headerSessionId);
            if (session == null) {
                log.warn("X-Session-Id={} 没有对应的已连接场景页面", headerSessionId);
            }
            return session;
        }

        if (!properties.isSingleSessionFallback()) {
            return null;
        }

        // 兜底：本次调用没带 X-Session-Id（Python 侧还没打通，见方案 §2.8）。
        // 只有在**恰好一条**连接时才敢猜，两条以上宁可报错，避免多用户时推错浏览器。
        SceneSession only = null;
        for (SceneSession candidate : sessions.values()) {
            if (!candidate.isConnected()) {
                continue;
            }
            if (only != null) {
                log.warn("工具调用未携带 X-Session-Id，且存在多条场景连接，拒绝猜测路由");
                return null;
            }
            only = candidate;
        }
        if (only != null) {
            log.warn("工具调用未携带 X-Session-Id，按唯一连接兜底路由到 sessionId={}",
                only.getSessionId());
        }
        return only;
    }

    /**
     * 回收空壳会话：既没有连接、也没有在途挂起项的，留一段时间后丢掉。
     *
     * <p>断开时不能立刻删 —— 挂起项要跨重连存活（方案 §2.3）。但用户永久关掉页面后，
     * 会话对象不该无限留在内存里。</p>
     */
    @Scheduled(fixedDelay = 300_000L, initialDelay = 300_000L)
    public void evictIdleSessions() {
        long deadline = System.currentTimeMillis() - properties.getIdleEvictMs();
        sessions.values().removeIf(session -> {
            if (session.isConnected() || session.hasPending() || session.getLastSeenAt() > deadline) {
                return false;
            }
            log.info("回收空闲场景会话: sessionId={}", session.getSessionId());
            return true;
        });
    }

    /** 当前已连接的会话快照，联调 / 排障用（未连接的不算）。 */
    public java.util.List<SceneSession> connectedSessions() {
        return sessions.values().stream().filter(SceneSession::isConnected).toList();
    }

    /** 当前已连接会话数，健康检查 / 排障用。 */
    public int connectedCount() {
        int count = 0;
        for (SceneSession session : sessions.values()) {
            if (session.isConnected()) {
                count++;
            }
        }
        return count;
    }

    public record BindResult(SceneSession session, WebSocketSession evicted) {
    }
}
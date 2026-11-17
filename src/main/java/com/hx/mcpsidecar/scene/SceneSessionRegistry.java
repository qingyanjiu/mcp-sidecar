package com.hx.mcpsidecar.scene;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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

    /** 精确的会话数，避免并发 bind 时 size() 近似值同时通过上限检查。 */
    private final AtomicInteger sessionCount = new AtomicInteger();

    private final SceneProperties properties;

    public SceneSessionRegistry(SceneProperties properties) {
        this.properties = properties;
    }

    /**
     * 归档一条 {@code hello}。
     *
     * <p>同一 sessionId 的重连会复用会话并顶掉旧 socket；但只有同一个 userId 才能重连，
     * 否则返回 {@link BindStatus#USER_MISMATCH}，避免知道 sessionId 的其他登录用户顶替连接。
     * 新建 sessionId 时还受 {@link SceneProperties#getMaxSessions()} 限制。</p>
     *
     * @return 绑定结果；调用方负责关闭 {@link BindResult#evicted()}（如果有）
     */
    public BindResult bind(String sessionId, String userId, WebSocketSession socket) {
        AtomicReference<BindResult> result = new AtomicReference<>();
        // compute 对同一个 key 串行执行，把“查归属 + 判上限 + 绑定”合并成一次原子操作。
        sessions.compute(sessionId, (id, existing) -> {
            SceneSession session = existing;
            if (session == null) {
                if (sessionCount.incrementAndGet() > properties.getMaxSessions()) {
                    sessionCount.decrementAndGet();
                    log.warn("场景会话数达到上限，拒绝新会话: max={}", properties.getMaxSessions());
                    result.set(new BindResult(null, null, BindStatus.CAPACITY_EXCEEDED));
                    return null;
                }
                session = new SceneSession(id);
            }

            if (session.isOwnedByDifferentUser(userId)) {
                log.warn("sessionId 已被其他用户占用，拒绝绑定: sessionId={}, owner={}, requester={}",
                        sessionId, session.getUserId(), userId);
                result.set(new BindResult(session, null, BindStatus.USER_MISMATCH));
                return session;
            }

            WebSocketSession old = session.bind(userId, socket);
            if (old != null) {
                log.info("场景会话重连，顶替旧连接: ws-session-id={}, user-id={}", sessionId, userId);
            } else {
                log.info("场景会话接入: ws-session-id={}, user-id={}", sessionId, userId);
            }
            result.set(new BindResult(session, old, BindStatus.BOUND));
            return session;
        });
        return result.get();
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
            sessionCount.decrementAndGet();
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

    public record BindResult(SceneSession session, WebSocketSession evicted, BindStatus status) {
    }

    public enum BindStatus {
        BOUND,
        USER_MISMATCH,
        CAPACITY_EXCEEDED
    }
}
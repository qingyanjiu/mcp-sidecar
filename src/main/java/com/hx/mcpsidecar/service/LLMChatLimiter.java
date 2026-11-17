package com.hx.mcpsidecar.service;

import com.hx.mcpsidecar.config.ws.LLMConfiguration;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class LLMChatLimiter {

    @Autowired
    private LLMConfiguration llmConfiguration;

    // 信号量
    private Semaphore requestPool = null;
    // 当前连接的用户及其占用数，用来校验释放信号量是否合法，防止随意调用释放信号量接口。
    // 用计数是因为同一个用户可能同时开多个会话（多个 WS 连接 / MCP 会话），
    // 每次 tryToChat 都占一个许可，必须按次数归还。
    // 计数必须是普通 Integer + ConcurrentHashMap.compute：增减和删除在同一次原子操作里完成，
    // 否则 completeChat 并发执行时会重复 release，信号量会被放大。
    private Map<String, Integer> connectedUserMap = new ConcurrentHashMap<>();

    // 初始化信号量对象
    @PostConstruct
    public void init() {
        requestPool = new Semaphore(llmConfiguration.getLimitSize());
    }

    /**
     * 尝试连接聊天
     * @return
     */
    public boolean tryToChat(String userId) {
        try {
            if (!requestPool.tryAcquire(3000, TimeUnit.MILLISECONDS)) {
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("尝试获取对话许可失败，队列已满");
            return false;
        }
        // 许可已拿到，计数必须成功登记；compute 对同一 userId 串行，不会丢计数。
        connectedUserMap.compute(userId, (key, count) -> count == null ? 1 : count + 1);
        return true;
    }

    /**
     * 结束一轮聊天。
     *
     * <p>每个许可只允许释放一次：计数减到 0 时同一次 compute 里移除 key，
     * 并发调用 completeChat 不会重复 release。
     */
    public boolean completeChat(String userId) {
        AtomicBoolean released = new AtomicBoolean(false);
        connectedUserMap.computeIfPresent(userId, (key, count) -> {
            requestPool.release();
            released.set(true);
            return count > 1 ? count - 1 : null;
        });
        return released.get();
    }
}

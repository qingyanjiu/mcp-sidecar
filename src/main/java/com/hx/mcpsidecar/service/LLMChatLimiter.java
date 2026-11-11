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
import java.util.concurrent.atomic.AtomicInteger;

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
    private Map<String, AtomicInteger> connectedUserMap = new ConcurrentHashMap<>();

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
        boolean ret = false;
        try {
            ret = requestPool.tryAcquire(3000, TimeUnit.MILLISECONDS);
            if (ret) {
                connectedUserMap.computeIfAbsent(userId, k -> new AtomicInteger()).incrementAndGet();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("尝试获取对话许可失败，队列已满");
        }
        return ret;
    }

    /**
     * 结束一轮聊天
     */
    public boolean completeChat(String userId) {
        AtomicInteger count = connectedUserMap.get(userId);
        // 如果该用户正在聊天，则可以释放，否则不释放信号量
        if (count == null) {
            return false;
        }
        requestPool.release();
        if (count.decrementAndGet() <= 0) {
            connectedUserMap.remove(userId, count);
        }
        return true;
    }
}

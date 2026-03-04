package com.hx.mcpsidecar.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class LLMChatLimiter {

    @Value("${llm.chat.limitSize}")
    private int limitSize;

    // 信号量
    private Semaphore requestPool = null;
    // 当前连接的用户，用来校验释放信号量是否合法，防止随意调用释放信号量接口
    private Map<String, Object> connectedUserMap = new ConcurrentHashMap<>();

    // 初始化信号量对象
    @PostConstruct
    public void init() {
        requestPool = new Semaphore(limitSize);
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
                connectedUserMap.putIfAbsent(userId, true);
            }
        } catch (InterruptedException e) {
            log.info("尝试获取对话许可失败，队列已满");
        }
        return ret;
    }

    /**
     * 结束一轮聊天
     */
    public boolean completeChat(String userId) {
        boolean ret = false;
        // 如果该用户正在聊天，则可以释放，否则不释放信号量
        if (connectedUserMap.containsKey(userId)) {
            requestPool.release();
            connectedUserMap.remove(userId);
            ret = true;
        }
        return ret;
    }
}

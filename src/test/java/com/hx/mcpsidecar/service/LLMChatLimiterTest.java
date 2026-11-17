package com.hx.mcpsidecar.service;

import com.hx.mcpsidecar.config.ws.LLMConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("LLM 对话限流")
class LLMChatLimiterTest {

    private LLMChatLimiter newLimiter(int limit) {
        LLMConfiguration configuration = new LLMConfiguration();
        configuration.setLimitSize(limit);
        LLMChatLimiter limiter = new LLMChatLimiter();
        ReflectionTestUtils.setField(limiter, "llmConfiguration", configuration);
        limiter.init();
        return limiter;
    }

    @Test
    @DisplayName("并发 completeChat 只释放一次许可")
    void concurrentCompleteOnlyReleasesOnce() throws Exception {
        LLMChatLimiter limiter = newLimiter(1);
        assertTrue(limiter.tryToChat("user-1"));

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    return limiter.completeChat("user-1");
                }));
            }
            start.countDown();

            long released = 0;
            for (Future<Boolean> future : futures) {
                if (future.get(5, TimeUnit.SECONDS)) {
                    released++;
                }
            }
            assertEquals(1, released, "同一个许可只能释放一次");
        } finally {
            pool.shutdownNow();
        }

        Semaphore poolSemaphore = (Semaphore) ReflectionTestUtils.getField(limiter, "requestPool");
        assertEquals(1, poolSemaphore.availablePermits(), "信号量不能被重复 release 放大");
        assertFalse(limiter.completeChat("user-1"));
        assertEquals(1, poolSemaphore.availablePermits());
    }

    @Test
    @DisplayName("多会话按次数占用和归还")
    void multipleSessionsUseMatchingPermits() {
        LLMChatLimiter limiter = newLimiter(2);
        assertTrue(limiter.tryToChat("user-1"));
        assertTrue(limiter.tryToChat("user-1"));

        assertTrue(limiter.completeChat("user-1"));
        assertTrue(limiter.completeChat("user-1"));
        assertFalse(limiter.completeChat("user-1"));

        Semaphore poolSemaphore = (Semaphore) ReflectionTestUtils.getField(limiter, "requestPool");
        assertEquals(2, poolSemaphore.availablePermits());
        assertTrue(limiter.tryToChat("user-1"));
    }
}

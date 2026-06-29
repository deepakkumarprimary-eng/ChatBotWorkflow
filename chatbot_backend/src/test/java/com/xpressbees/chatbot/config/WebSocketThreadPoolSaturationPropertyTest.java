package com.xpressbees.chatbot.config;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for no task loss under thread pool saturation.
 *
 * <p><b>Feature: websocket-thread-pool, Property 5: No Task Loss Under Saturation</b></p>
 *
 * <p>For any bounded thread pool configured with CallerRunsPolicy, and for any number
 * of tasks submitted (including more than queue + max), the count of completed tasks
 * SHALL equal the count of submitted tasks — no task is ever discarded.</p>
 *
 * <p><b>Validates: Requirements 5.1, 5.4</b></p>
 */
class WebSocketThreadPoolSaturationPropertyTest {

    @Property(tries = 100)
    @Tag("websocket-thread-pool")
    @Label("Feature: websocket-thread-pool, Property 5: all submitted tasks complete under saturation")
    void allTasksCompleteUnderSaturation(
            @ForAll @IntRange(min = 1, max = 4) int coreSize,
            @ForAll @IntRange(min = 0, max = 4) int maxHeadroom,
            @ForAll @IntRange(min = 1, max = 5) int queueCapacity,
            @ForAll @IntRange(min = 1, max = 50) int taskCount) {

        int maxSize = coreSize + maxHeadroom;

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("ws-saturation-test-");
        executor.setRejectedExecutionHandler(new LoggingCallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();

        AtomicInteger completedCount = new AtomicInteger(0);
        CountDownLatch allSubmitted = new CountDownLatch(1);
        CountDownLatch allCompleted = new CountDownLatch(taskCount);

        try {
            for (int i = 0; i < taskCount; i++) {
                executor.execute(() -> {
                    completedCount.incrementAndGet();
                    allCompleted.countDown();
                });
            }
            allSubmitted.countDown();

            boolean finished = allCompleted.await(30, TimeUnit.SECONDS);
            assertThat(finished)
                    .as("All %d tasks should complete within timeout", taskCount)
                    .isTrue();
            assertThat(completedCount.get()).isEqualTo(taskCount);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } finally {
            executor.shutdown();
        }
    }
}

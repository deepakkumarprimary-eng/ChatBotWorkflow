package com.xpressbees.chatbot.config;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for WebSocket thread pool configuration propagation and thread naming.
 *
 * <p><b>Feature: websocket-thread-pool</b></p>
 */
class WebSocketThreadPoolPropertyTest {

    // --- Property 1: Configuration Propagation ---

    @Property(tries = 100)
    @Tag("websocket-thread-pool")
    @Label("Feature: websocket-thread-pool, Property 1: executor corePoolSize matches configured value")
    void executorCorePoolSizeMatchesConfig(
            @ForAll @IntRange(min = 1, max = 100) int coreSize,
            @ForAll @IntRange(min = 0, max = 100) int headroom,
            @ForAll @IntRange(min = 1, max = 500) int queueCapacity) {

        int maxSize = coreSize + headroom;

        ThreadPoolTaskExecutor executor = buildExecutor(coreSize, maxSize, queueCapacity, "ws-test-");
        try {
            assertThat(executor.getCorePoolSize()).isEqualTo(coreSize);
            assertThat(executor.getMaxPoolSize()).isEqualTo(maxSize);
            assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity() +
                    executor.getThreadPoolExecutor().getQueue().size()).isEqualTo(queueCapacity);
        } finally {
            executor.shutdown();
        }
    }

    // --- Property 2: Thread Naming Prefix ---

    @Property(tries = 100)
    @Tag("websocket-thread-pool")
    @Label("Feature: websocket-thread-pool, Property 2: inbound executor threads have ws-inbound- prefix")
    void inboundExecutorThreadsHaveCorrectPrefix(
            @ForAll @IntRange(min = 1, max = 20) int coreSize) {

        ThreadPoolTaskExecutor executor = buildExecutor(coreSize, coreSize, 10, "ws-inbound-");
        try {
            AtomicReference<String> threadName = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);

            executor.execute(() -> {
                threadName.set(Thread.currentThread().getName());
                latch.countDown();
            });

            boolean completed = latch.await(5, TimeUnit.SECONDS);
            assertThat(completed).isTrue();
            assertThat(threadName.get()).startsWith("ws-inbound-");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } finally {
            executor.shutdown();
        }
    }

    @Property(tries = 100)
    @Tag("websocket-thread-pool")
    @Label("Feature: websocket-thread-pool, Property 2: outbound executor threads have ws-outbound- prefix")
    void outboundExecutorThreadsHaveCorrectPrefix(
            @ForAll @IntRange(min = 1, max = 20) int coreSize) {

        ThreadPoolTaskExecutor executor = buildExecutor(coreSize, coreSize, 10, "ws-outbound-");
        try {
            AtomicReference<String> threadName = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);

            executor.execute(() -> {
                threadName.set(Thread.currentThread().getName());
                latch.countDown();
            });

            boolean completed = latch.await(5, TimeUnit.SECONDS);
            assertThat(completed).isTrue();
            assertThat(threadName.get()).startsWith("ws-outbound-");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } finally {
            executor.shutdown();
        }
    }

    private ThreadPoolTaskExecutor buildExecutor(int coreSize, int maxSize, int queueCapacity, String prefix) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(prefix);
        executor.setRejectedExecutionHandler(new LoggingCallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        executor.initialize();
        return executor;
    }
}

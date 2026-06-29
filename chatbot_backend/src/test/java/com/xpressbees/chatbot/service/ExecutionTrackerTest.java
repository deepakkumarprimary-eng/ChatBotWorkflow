package com.xpressbees.chatbot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ExecutionTracker covering tryStart/complete lifecycle,
 * shutdown rejection behavior, and concurrent increment/decrement integrity.
 *
 * Validates: Requirements 5.2, 5.3, 5.4
 */
class ExecutionTrackerTest {

    private ExecutionTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new ExecutionTracker();
    }

    @Nested
    @DisplayName("Basic lifecycle")
    class BasicLifecycle {

        @Test
        @DisplayName("tryStart() returns true and increments active count")
        void tryStartReturnsTrueAndIncrements() {
            boolean result = tracker.tryStart();

            assertThat(result).isTrue();
            assertThat(tracker.getActiveCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("complete() decrements active count")
        void completeDecrementsActiveCount() {
            tracker.tryStart();
            tracker.complete();

            assertThat(tracker.getActiveCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("Multiple tryStart/complete pairs track correctly")
        void multipleTryStartCompletePairs() {
            tracker.tryStart();
            tracker.tryStart();
            tracker.tryStart();

            assertThat(tracker.getActiveCount()).isEqualTo(3);

            tracker.complete();
            assertThat(tracker.getActiveCount()).isEqualTo(2);

            tracker.complete();
            tracker.complete();
            assertThat(tracker.getActiveCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("getActiveCount() returns 0 initially")
        void activeCountIsZeroInitially() {
            assertThat(tracker.getActiveCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("isShuttingDown() returns false initially")
        void isNotShuttingDownInitially() {
            assertThat(tracker.isShuttingDown()).isFalse();
        }
    }

    @Nested
    @DisplayName("Shutdown behavior")
    class ShutdownBehavior {

        @Test
        @DisplayName("tryStart() returns false when shutting down")
        void tryStartReturnsFalseWhenShuttingDown() {
            tracker.beginShutdown();

            boolean result = tracker.tryStart();

            assertThat(result).isFalse();
            assertThat(tracker.getActiveCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("beginShutdown() sets shuttingDown flag")
        void beginShutdownSetsFlag() {
            tracker.beginShutdown();

            assertThat(tracker.isShuttingDown()).isTrue();
        }

        @Test
        @DisplayName("Already-started executions can still complete after shutdown begins")
        void existingExecutionsCanCompleteAfterShutdown() {
            tracker.tryStart();
            tracker.tryStart();

            tracker.beginShutdown();

            assertThat(tracker.getActiveCount()).isEqualTo(2);

            tracker.complete();
            assertThat(tracker.getActiveCount()).isEqualTo(1);

            tracker.complete();
            assertThat(tracker.getActiveCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("Multiple tryStart() calls after shutdown all return false")
        void multipleTryStartAfterShutdownAllReturnFalse() {
            tracker.beginShutdown();

            assertThat(tracker.tryStart()).isFalse();
            assertThat(tracker.tryStart()).isFalse();
            assertThat(tracker.tryStart()).isFalse();
            assertThat(tracker.getActiveCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Concurrent increment/decrement integrity")
    class ConcurrencyIntegrity {

        @Test
        @DisplayName("Concurrent tryStart/complete maintains correct active count")
        void concurrentTryStartAndCompleteMaintainsIntegrity() throws InterruptedException {
            int threadCount = 100;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        if (tracker.tryStart()) {
                            // Simulate some work
                            Thread.sleep(1);
                            tracker.complete();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            doneLatch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            assertThat(tracker.getActiveCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("Concurrent tryStart increments are all counted")
        void concurrentTryStartAllCounted() throws InterruptedException {
            int threadCount = 50;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        if (tracker.tryStart()) {
                            successCount.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            doneLatch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            assertThat(tracker.getActiveCount()).isEqualTo(successCount.get());
            assertThat(successCount.get()).isEqualTo(threadCount);
        }

        @Test
        @DisplayName("beginShutdown during concurrent tryStart rejects subsequent calls")
        void shutdownDuringConcurrentAccess() throws InterruptedException {
            int threadCount = 50;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger acceptedCount = new AtomicInteger(0);
            AtomicInteger rejectedCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        // Trigger shutdown midway through
                        if (index == threadCount / 2) {
                            tracker.beginShutdown();
                        }
                        if (tracker.tryStart()) {
                            acceptedCount.incrementAndGet();
                            tracker.complete();
                        } else {
                            rejectedCount.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            doneLatch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            // All threads were either accepted or rejected
            assertThat(acceptedCount.get() + rejectedCount.get()).isEqualTo(threadCount);
            // At least some were rejected (shutdown happened midway)
            assertThat(rejectedCount.get()).isGreaterThan(0);
            // Active count should be 0 since all accepted ones called complete()
            assertThat(tracker.getActiveCount()).isEqualTo(0);
        }
    }
}

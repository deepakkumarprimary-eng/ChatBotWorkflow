package com.xpressbees.chatbot.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for CorrelationIdManager covering the set/get/clear lifecycle
 * and thread isolation behavior (MDC is thread-local).
 *
 * Validates: Requirements 2.2, 2.4
 */
class CorrelationIdManagerTest {

    private final CorrelationIdManager manager = new CorrelationIdManager();

    @AfterEach
    void cleanupMdc() {
        MDC.clear();
    }

    @Nested
    @DisplayName("Set/Get/Clear lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("get() returns null when no correlation ID is set")
        void getReturnsNullWhenNotSet() {
            assertThat(manager.get()).isNull();
        }

        @Test
        @DisplayName("set() stores the session ID and get() retrieves it")
        void setAndGetRoundTrip() {
            manager.set("session-123");

            assertThat(manager.get()).isEqualTo("session-123");
        }

        @Test
        @DisplayName("clear() removes the correlation ID from MDC")
        void clearRemovesCorrelationId() {
            manager.set("session-456");
            manager.clear();

            assertThat(manager.get()).isNull();
        }

        @Test
        @DisplayName("set() overwrites a previously set correlation ID")
        void setOverwritesPreviousValue() {
            manager.set("session-first");
            manager.set("session-second");

            assertThat(manager.get()).isEqualTo("session-second");
        }

        @Test
        @DisplayName("clear() is safe to call when no correlation ID is set")
        void clearWhenNothingSetDoesNotThrow() {
            manager.clear();

            assertThat(manager.get()).isNull();
        }
    }

    @Nested
    @DisplayName("Thread isolation (MDC is thread-local)")
    class ThreadIsolation {

        @Test
        @DisplayName("Correlation ID set in one thread is not visible in another thread")
        void correlationIdIsNotVisibleAcrossThreads() throws InterruptedException {
            manager.set("main-thread-session");

            AtomicReference<String> otherThreadValue = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);

            Thread otherThread = new Thread(() -> {
                otherThreadValue.set(manager.get());
                latch.countDown();
            });
            otherThread.start();
            latch.await();

            assertThat(otherThreadValue.get()).isNull();
            assertThat(manager.get()).isEqualTo("main-thread-session");
        }

        @Test
        @DisplayName("Each thread maintains its own independent correlation ID")
        void eachThreadHasIndependentCorrelationId() throws InterruptedException {
            manager.set("main-session");

            AtomicReference<String> childThreadValue = new AtomicReference<>();
            CountDownLatch setLatch = new CountDownLatch(1);
            CountDownLatch readLatch = new CountDownLatch(1);

            Thread childThread = new Thread(() -> {
                CorrelationIdManager childManager = new CorrelationIdManager();
                childManager.set("child-session");
                childThreadValue.set(childManager.get());
                setLatch.countDown();
                try {
                    readLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                childManager.clear();
            });
            childThread.start();
            setLatch.await();

            // Main thread still has its own value
            assertThat(manager.get()).isEqualTo("main-session");
            // Child thread had its own value
            assertThat(childThreadValue.get()).isEqualTo("child-session");

            readLatch.countDown();
            childThread.join();
        }

        @Test
        @DisplayName("Clearing in one thread does not affect another thread")
        void clearInOneThreadDoesNotAffectAnother() throws InterruptedException {
            manager.set("main-session");

            CountDownLatch clearDone = new CountDownLatch(1);

            Thread otherThread = new Thread(() -> {
                CorrelationIdManager otherManager = new CorrelationIdManager();
                otherManager.set("other-session");
                otherManager.clear();
                clearDone.countDown();
            });
            otherThread.start();
            clearDone.await();
            otherThread.join();

            // Main thread value is unaffected
            assertThat(manager.get()).isEqualTo("main-session");
        }
    }
}

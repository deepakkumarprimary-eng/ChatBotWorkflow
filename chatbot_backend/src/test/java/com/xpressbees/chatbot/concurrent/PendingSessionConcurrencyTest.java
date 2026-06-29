package com.xpressbees.chatbot.concurrent;

import com.xpressbees.chatbot.service.PendingSessionStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Concurrency tests for PendingSessionStore verifying thread-safety of
 * session registration and atomic consume semantics under parallel execution.
 *
 * Uses a ConcurrentHashMap-backed mock of StringRedisTemplate to simulate
 * Redis behavior without requiring a real Redis instance.
 *
 * Validates: Requirements 6.2, 6.4
 */
class PendingSessionConcurrencyTest {

    private static final int THREAD_COUNT = 20;

    /**
     * Creates a PendingSessionStore backed by a ConcurrentHashMap simulating Redis.
     * - opsForValue().set(key, value, duration) → put in map
     * - delete(key) → remove from map, return true if existed
     */
    private PendingSessionStore createStoreWithSimulatedRedis(ConcurrentHashMap<String, String> backingMap) {
        StringRedisTemplate mockTemplate = Mockito.mock(StringRedisTemplate.class);

        @SuppressWarnings("unchecked")
        ValueOperations<String, String> mockValueOps = Mockito.mock(ValueOperations.class);

        when(mockTemplate.opsForValue()).thenReturn(mockValueOps);

        // Simulate SET: put key/value into backing map
        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String value = invocation.getArgument(1);
            backingMap.put(key, value);
            return null;
        }).when(mockValueOps).set(anyString(), anyString(), any(Duration.class));

        // Simulate DELETE: remove from map, return true if existed
        when(mockTemplate.delete(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return backingMap.remove(key) != null;
        });

        return new PendingSessionStore(mockTemplate, 5L);
    }

    @Test
    @DisplayName("Concurrent registrations: all N sessionIds are stored without overwriting")
    void concurrentRegistrations_allSessionIdsStored() throws InterruptedException {
        // Validates: Requirement 6.2
        // Multiple concurrent chat.init requests should store all generated sessionIds without loss.

        ConcurrentHashMap<String, String> backingMap = new ConcurrentHashMap<>();
        PendingSessionStore store = createStoreWithSimulatedRedis(backingMap);

        // Generate N distinct session IDs
        List<String> sessionIds = new ArrayList<>();
        for (int i = 0; i < THREAD_COUNT; i++) {
            sessionIds.add(UUID.randomUUID().toString());
        }

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
        List<Boolean> results = Collections.synchronizedList(new ArrayList<>());

        // Submit all registration tasks — they block on startLatch
        for (int i = 0; i < THREAD_COUNT; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // All threads start simultaneously
                    boolean registered = store.register(sessionIds.get(index));
                    results.add(registered);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Release all threads at once
        startLatch.countDown();

        // Wait for all to complete
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue().withFailMessage("Threads did not complete within timeout");

        // All registrations should succeed
        assertThat(results).hasSize(THREAD_COUNT);
        assertThat(results).allMatch(r -> r == Boolean.TRUE,
                "All concurrent registrations should return true");

        // All session IDs should be present in the backing map
        for (String sessionId : sessionIds) {
            String key = "pending-session:" + sessionId;
            assertThat(backingMap).containsKey(key)
                    .withFailMessage("Session ID %s should be stored in the map", sessionId);
        }

        // No overwrites: map should contain exactly N entries
        assertThat(backingMap).hasSize(THREAD_COUNT);
    }

    @Test
    @DisplayName("Concurrent consume: exactly one thread returns true, all others return false")
    void concurrentConsume_exactlyOneReturnsTrue() throws InterruptedException {
        // Validates: Requirement 6.4
        // When N concurrent threads call consumePendingSession with the same sessionId,
        // exactly one should get true, all others should get false.

        ConcurrentHashMap<String, String> backingMap = new ConcurrentHashMap<>();
        PendingSessionStore store = createStoreWithSimulatedRedis(backingMap);

        // Register a single session
        String sessionId = UUID.randomUUID().toString();
        store.register(sessionId);

        // Verify it's registered
        String key = "pending-session:" + sessionId;
        assertThat(backingMap).containsKey(key);

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger trueCount = new AtomicInteger(0);
        AtomicInteger falseCount = new AtomicInteger(0);

        // Submit N consume tasks for the same sessionId
        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // All threads start simultaneously
                    boolean consumed = store.consume(sessionId);
                    if (consumed) {
                        trueCount.incrementAndGet();
                    } else {
                        falseCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Release all threads at once
        startLatch.countDown();

        // Wait for all to complete
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue().withFailMessage("Threads did not complete within timeout");

        // Exactly one thread should get true
        assertThat(trueCount.get())
                .as("Exactly one thread should successfully consume the session")
                .isEqualTo(1);

        // All other threads should get false
        assertThat(falseCount.get())
                .as("All other threads should receive false from consume")
                .isEqualTo(THREAD_COUNT - 1);

        // Session should no longer be in the map
        assertThat(backingMap).doesNotContainKey(key);
    }

    @Test
    @DisplayName("Concurrent consume of non-existent session: all threads return false")
    void concurrentConsume_nonExistentSession_allReturnFalse() throws InterruptedException {
        // Validates: Requirement 6.4 (edge case)
        // If a session was never registered, all concurrent consume calls should return false.

        ConcurrentHashMap<String, String> backingMap = new ConcurrentHashMap<>();
        PendingSessionStore store = createStoreWithSimulatedRedis(backingMap);

        String nonExistentSessionId = UUID.randomUUID().toString();

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger trueCount = new AtomicInteger(0);
        AtomicInteger falseCount = new AtomicInteger(0);

        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    boolean consumed = store.consume(nonExistentSessionId);
                    if (consumed) {
                        trueCount.incrementAndGet();
                    } else {
                        falseCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue().withFailMessage("Threads did not complete within timeout");

        // No thread should get true
        assertThat(trueCount.get())
                .as("No thread should succeed when consuming a non-existent session")
                .isEqualTo(0);

        // All threads should get false
        assertThat(falseCount.get())
                .as("All threads should receive false for a non-existent session")
                .isEqualTo(THREAD_COUNT);
    }

    @Test
    @DisplayName("Mixed concurrent register and consume: registration completes and consume succeeds exactly once")
    void mixedConcurrentRegisterAndConsume() throws InterruptedException, ExecutionException {
        // Validates: Requirements 6.2, 6.4
        // Register N sessions concurrently, then consume each concurrently.
        // Each session should be consumable exactly once.

        ConcurrentHashMap<String, String> backingMap = new ConcurrentHashMap<>();
        PendingSessionStore store = createStoreWithSimulatedRedis(backingMap);

        int sessionCount = 10;
        List<String> sessionIds = new ArrayList<>();
        for (int i = 0; i < sessionCount; i++) {
            sessionIds.add(UUID.randomUUID().toString());
        }

        // Phase 1: Register all sessions concurrently
        ExecutorService registerExecutor = Executors.newFixedThreadPool(sessionCount);
        CountDownLatch registerStart = new CountDownLatch(1);
        CountDownLatch registerDone = new CountDownLatch(sessionCount);

        for (String sid : sessionIds) {
            registerExecutor.submit(() -> {
                try {
                    registerStart.await();
                    store.register(sid);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    registerDone.countDown();
                }
            });
        }

        registerStart.countDown();
        boolean registerCompleted = registerDone.await(10, TimeUnit.SECONDS);
        registerExecutor.shutdown();
        assertThat(registerCompleted).isTrue();

        // Verify all registered
        assertThat(backingMap).hasSize(sessionCount);

        // Phase 2: Consume each session with multiple threads racing
        int consumeThreadsPerSession = 5;
        ExecutorService consumeExecutor = Executors.newFixedThreadPool(sessionCount * consumeThreadsPerSession);
        CountDownLatch consumeStart = new CountDownLatch(1);
        CountDownLatch consumeDone = new CountDownLatch(sessionCount * consumeThreadsPerSession);
        ConcurrentHashMap<String, AtomicInteger> trueCounts = new ConcurrentHashMap<>();

        for (String sid : sessionIds) {
            trueCounts.put(sid, new AtomicInteger(0));
            for (int t = 0; t < consumeThreadsPerSession; t++) {
                consumeExecutor.submit(() -> {
                    try {
                        consumeStart.await();
                        boolean consumed = store.consume(sid);
                        if (consumed) {
                            trueCounts.get(sid).incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        consumeDone.countDown();
                    }
                });
            }
        }

        consumeStart.countDown();
        boolean consumeCompleted = consumeDone.await(10, TimeUnit.SECONDS);
        consumeExecutor.shutdown();
        assertThat(consumeCompleted).isTrue();

        // Each session should be consumed exactly once
        for (String sid : sessionIds) {
            assertThat(trueCounts.get(sid).get())
                    .as("Session %s should be consumed exactly once", sid)
                    .isEqualTo(1);
        }

        // Backing map should be empty after all consumes
        assertThat(backingMap).isEmpty();
    }
}

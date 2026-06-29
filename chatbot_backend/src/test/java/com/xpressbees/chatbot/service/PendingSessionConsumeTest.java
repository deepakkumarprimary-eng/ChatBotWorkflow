package com.xpressbees.chatbot.service;

import net.jqwik.api.*;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Feature: redis-caching-and-performance, Property 5: Pending session atomic consume

/**
 * Property-based tests for PendingSessionStore consume logic.
 * - Property 5: Pending session atomic consume (exactly-once)
 *
 * Validates: Requirements 3.2, 3.3
 *
 * Uses a simulated Redis store (ConcurrentHashMap) behind a mocked StringRedisTemplate
 * to verify the exactly-once consume semantics without needing a real Redis instance.
 */
class PendingSessionConsumeTest {

    /**
     * Creates a PendingSessionStore backed by a ConcurrentHashMap that simulates Redis behavior.
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

    @Property(tries = 100)
    @Tag("Feature: redis-caching-and-performance, Property 5: Pending session atomic consume")
    void registeredSessionCanBeConsumedExactlyOnce(@ForAll("uuidStrings") String sessionId) {
        // Validates: Requirements 3.2, 3.3
        // A registered session ID can be consumed exactly once (returns true).

        ConcurrentHashMap<String, String> backingMap = new ConcurrentHashMap<>();
        PendingSessionStore store = createStoreWithSimulatedRedis(backingMap);

        // Register the session
        boolean registered = store.register(sessionId);
        assert registered : "Registration should succeed for sessionId=" + sessionId;

        // First consume should return true
        boolean firstConsume = store.consume(sessionId);
        assert firstConsume :
                "First consume of a registered session should return true. sessionId=" + sessionId;
    }

    @Property(tries = 100)
    @Tag("Feature: redis-caching-and-performance, Property 5: Pending session atomic consume")
    void secondConsumeOfSameSessionReturnsFalse(@ForAll("uuidStrings") String sessionId) {
        // Validates: Requirements 3.2, 3.3
        // Consuming the same session ID a second time (without re-registration) returns false.

        ConcurrentHashMap<String, String> backingMap = new ConcurrentHashMap<>();
        PendingSessionStore store = createStoreWithSimulatedRedis(backingMap);

        // Register the session
        store.register(sessionId);

        // First consume should succeed
        boolean firstConsume = store.consume(sessionId);
        assert firstConsume :
                "First consume should return true. sessionId=" + sessionId;

        // Second consume should fail
        boolean secondConsume = store.consume(sessionId);
        assert !secondConsume :
                "Second consume of the same session (without re-registration) should return false. sessionId=" + sessionId;
    }

    @Property(tries = 100)
    @Tag("Feature: redis-caching-and-performance, Property 5: Pending session atomic consume")
    void consumingNeverRegisteredSessionReturnsFalse(@ForAll("uuidStrings") String sessionId) {
        // Validates: Requirements 3.2, 3.3
        // Consuming a never-registered session ID returns false.

        ConcurrentHashMap<String, String> backingMap = new ConcurrentHashMap<>();
        PendingSessionStore store = createStoreWithSimulatedRedis(backingMap);

        // Do NOT register the session — consume directly
        boolean consumed = store.consume(sessionId);
        assert !consumed :
                "Consuming a never-registered session should return false. sessionId=" + sessionId;
    }

    @Property(tries = 100)
    @Tag("Feature: redis-caching-and-performance, Property 5: Pending session atomic consume")
    void reRegisteredSessionCanBeConsumedAgain(@ForAll("uuidStrings") String sessionId) {
        // Validates: Requirements 3.2, 3.3
        // After consuming a session, re-registering it should allow a successful consume again.

        ConcurrentHashMap<String, String> backingMap = new ConcurrentHashMap<>();
        PendingSessionStore store = createStoreWithSimulatedRedis(backingMap);

        // Register → consume (first cycle)
        store.register(sessionId);
        boolean firstConsume = store.consume(sessionId);
        assert firstConsume :
                "First consume after register should return true. sessionId=" + sessionId;

        // Re-register → consume (second cycle)
        store.register(sessionId);
        boolean secondConsume = store.consume(sessionId);
        assert secondConsume :
                "Consume after re-registration should return true. sessionId=" + sessionId;
    }

    @Provide
    Arbitrary<String> uuidStrings() {
        // Use alphanumeric strings with a UUID-like length to allow jqwik to generate many random values
        return Arbitraries.strings().alpha().numeric().ofMinLength(8).ofMaxLength(36)
                .map(s -> UUID.nameUUIDFromBytes(s.getBytes()).toString());
    }
}

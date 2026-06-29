package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.config.WebSocketResilienceProperties;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Feature: websocket-resilience, Property 1: Session ID Mapping Lookup
// Feature: websocket-resilience, Property 3: Connection Count Invariant
// Feature: websocket-resilience, Property 4: Connection Rejection at Capacity

/**
 * Property-based tests for ConnectionRegistry (InMemoryConnectionRegistry).
 *
 * Validates: Requirements 1.1, 3.2, 3.3, 3.4, 3.5
 */
class ConnectionRegistryPropertyTest {

    private InMemoryConnectionRegistry createRegistry(int maxConnections) {
        WebSocketResilienceProperties properties = new WebSocketResilienceProperties();
        properties.setMaxConnections(maxConnections);
        return new InMemoryConnectionRegistry(properties);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Property 1: Session ID Mapping Lookup
    // For any pair of (stompId, appId) registered in the ConnectionRegistry,
    // looking up the application session ID by STOMP session ID shall always
    // return the correct application session ID, and vice versa.
    //
    // Validates: Requirements 1.1
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Property 1: Session ID Mapping Lookup — Generate random (stompId, appId) pairs,
     * register them, verify bidirectional lookup correctness (getApplicationSessionId
     * and getStompSessionId both return correct values).
     *
     * Validates: Requirements 1.1
     */
    @Property(tries = 100)
    @Tag("Feature: websocket-resilience, Property 1: Session ID Mapping Lookup")
    void registeredSessionsSupportBidirectionalLookup(
            @ForAll("stompAndAppIdPairs") List<String[]> pairs) {

        InMemoryConnectionRegistry registry = createRegistry(1000);

        for (String[] pair : pairs) {
            String stompId = pair[0];
            String appId = pair[1];
            registry.register(stompId, appId);
        }

        for (String[] pair : pairs) {
            String stompId = pair[0];
            String appId = pair[1];

            assertThat(registry.getApplicationSessionId(stompId))
                    .as("getApplicationSessionId(%s) should return %s", stompId, appId)
                    .isEqualTo(appId);

            assertThat(registry.getStompSessionId(appId))
                    .as("getStompSessionId(%s) should return %s", appId, stompId)
                    .isEqualTo(stompId);
        }
    }

    @Provide
    Arbitrary<List<String[]>> stompAndAppIdPairs() {
        // Generate 1-20 unique pairs to avoid collisions
        return Arbitraries.integers().between(1, 20).flatMap(count -> {
            List<String[]> pairs = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                pairs.add(new String[]{
                        "stomp-" + UUID.randomUUID(),
                        "app-" + UUID.randomUUID()
                });
            }
            return Arbitraries.just(pairs);
        });
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Property 3: Connection Count Invariant
    // For any sequence of register and unregister operations, the ConnectionRegistry's
    // active count shall always equal the number of successful registrations minus
    // the number of successful unregistrations, clamped to a minimum of zero.
    //
    // Validates: Requirements 3.3, 3.4, 3.5
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Property 3: Connection Count Invariant — Generate random sequences of
     * register/unregister, verify count equals registrations minus unregistrations
     * (clamped ≥ 0).
     *
     * Validates: Requirements 3.3, 3.4, 3.5
     */
    @Property(tries = 100)
    @Tag("Feature: websocket-resilience, Property 3: Connection Count Invariant")
    void connectionCountEqualsRegistrationsMinusUnregistrations(
            @ForAll("operationSequenceSize") int sequenceSize) {

        InMemoryConnectionRegistry registry = createRegistry(1000);
        List<String> registeredStompIds = new ArrayList<>();
        java.util.Random rng = new java.util.Random();
        int counter = 0;

        for (int i = 0; i < sequenceSize; i++) {
            // 70% chance of register, 30% chance of unregister
            boolean doRegister = registeredStompIds.isEmpty() || rng.nextDouble() < 0.7;

            if (doRegister) {
                // Always use unique IDs to avoid overwrites
                String stompId = "stomp-" + counter;
                String appId = "app-" + counter;
                counter++;
                boolean success = registry.register(stompId, appId);
                if (success) {
                    registeredStompIds.add(stompId);
                }
            } else {
                // Unregister a randomly chosen previously registered session
                int idx = rng.nextInt(registeredStompIds.size());
                String toRemove = registeredStompIds.remove(idx);
                registry.unregister(toRemove);
            }
        }

        // The active count should equal the number of sessions still registered
        assertThat(registry.getActiveCount())
                .as("Active count should equal registrations minus unregistrations")
                .isEqualTo(registeredStompIds.size());
    }

    @Provide
    Arbitrary<Integer> operationSequenceSize() {
        return Arbitraries.integers().between(5, 50);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Property 4: Connection Rejection at Capacity
    // For any configured maximum connection limit N, after N connections have been
    // successfully registered, the (N+1)th registration attempt shall be rejected
    // and the active count shall remain at N.
    //
    // Validates: Requirements 3.2
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Property 4: Connection Rejection at Capacity — For random capacities N
     * (between 1 and 100), fill to limit, verify (N+1)th registration is rejected
     * and count remains at N.
     *
     * Validates: Requirements 3.2
     */
    @Property(tries = 100)
    @Tag("Feature: websocket-resilience, Property 4: Connection Rejection at Capacity")
    void connectionRejectedAtCapacity(
            @ForAll @IntRange(min = 1, max = 100) int maxConnections) {

        InMemoryConnectionRegistry registry = createRegistry(maxConnections);

        // Fill to capacity
        for (int i = 0; i < maxConnections; i++) {
            boolean registered = registry.register("stomp-" + i, "app-" + i);
            assertThat(registered)
                    .as("Registration %d of %d should succeed", i + 1, maxConnections)
                    .isTrue();
        }

        assertThat(registry.getActiveCount())
                .as("Active count should be at max capacity")
                .isEqualTo(maxConnections);

        // Attempt (N+1)th registration — should be rejected
        boolean rejected = registry.register("stomp-overflow", "app-overflow");
        assertThat(rejected)
                .as("Registration beyond capacity should be rejected")
                .isFalse();

        // Count should remain at N
        assertThat(registry.getActiveCount())
                .as("Active count should remain at max after rejected registration")
                .isEqualTo(maxConnections);
    }

}

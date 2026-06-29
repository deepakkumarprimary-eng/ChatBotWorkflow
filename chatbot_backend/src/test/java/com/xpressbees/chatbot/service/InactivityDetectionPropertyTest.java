package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.config.WebSocketResilienceProperties;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Feature: websocket-resilience, Property 5: Inactivity Detection
// Feature: websocket-resilience, Property 6: Activity Recording Resets Timer

/**
 * Property-based tests for inactivity detection logic in InMemoryConnectionRegistry.
 *
 * Validates: Requirements 4.2, 4.5
 */
class InactivityDetectionPropertyTest {

    private InMemoryConnectionRegistry createRegistry(int maxConnections) {
        WebSocketResilienceProperties properties = new WebSocketResilienceProperties();
        properties.setMaxConnections(maxConnections);
        return new InMemoryConnectionRegistry(properties);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Property 5: Inactivity Detection
    // For any session with a lastActivityAt timestamp and any configured timeout
    // duration D, the session shall be identified as inactive if and only if the
    // elapsed time since lastActivityAt exceeds D.
    //
    // Validates: Requirements 4.2
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Property 5: Inactivity Detection — Register sessions, then verify that
     * getInactiveSessions with Duration.ZERO returns all sessions (since any
     * elapsed time > 0 exceeds a zero timeout), and getInactiveSessions with a
     * very large duration returns no sessions (since no elapsed time exceeds it).
     *
     * Validates: Requirements 4.2
     */
    @Property(tries = 100)
    @Tag("Feature: websocket-resilience, Property 5: Inactivity Detection")
    void sessionsAreInactiveIffElapsedTimeExceedsTimeout(
            @ForAll @IntRange(min = 1, max = 50) int sessionCount) throws InterruptedException {

        InMemoryConnectionRegistry registry = createRegistry(1000);

        // Register multiple sessions
        for (int i = 0; i < sessionCount; i++) {
            registry.register("stomp-" + i, "app-" + i);
        }

        // Small sleep to ensure some elapsed time from registration
        Thread.sleep(5);

        // With Duration.ZERO, all sessions should be inactive
        // (any elapsed time > 0 exceeds a zero-duration timeout)
        List<String> inactiveWithZeroTimeout = registry.getInactiveSessions(Duration.ZERO);
        assertThat(inactiveWithZeroTimeout)
                .as("All sessions should be inactive when timeout is Duration.ZERO")
                .hasSize(sessionCount);

        // With a very large duration (1 day), no session should be inactive
        // (no elapsed time since registration can exceed 1 day)
        List<String> inactiveWithLargeTimeout = registry.getInactiveSessions(Duration.ofDays(1));
        assertThat(inactiveWithLargeTimeout)
                .as("No session should be inactive when timeout is 1 day")
                .isEmpty();
    }

    /**
     * Property 5 (continued): For random timeout durations between 1ms and 100ms,
     * sessions registered before a sleep of that duration should be detected as
     * inactive, confirming the if-and-only-if relationship.
     *
     * Validates: Requirements 4.2
     */
    @Property(tries = 100)
    @Tag("Feature: websocket-resilience, Property 5: Inactivity Detection")
    void sessionsDetectedInactiveAfterTimeoutExpires(
            @ForAll @IntRange(min = 10, max = 50) int timeoutMs,
            @ForAll @IntRange(min = 1, max = 10) int sessionCount) throws InterruptedException {

        InMemoryConnectionRegistry registry = createRegistry(1000);

        // Register sessions
        for (int i = 0; i < sessionCount; i++) {
            registry.register("stomp-" + i, "app-" + i);
        }

        // Sleep longer than the timeout to ensure sessions become inactive
        Thread.sleep(timeoutMs + 20);

        // All sessions should now be inactive with the given timeout
        Duration timeout = Duration.ofMillis(timeoutMs);
        List<String> inactiveSessions = registry.getInactiveSessions(timeout);
        assertThat(inactiveSessions)
                .as("All sessions should be inactive after sleeping longer than timeout (%dms)", timeoutMs)
                .hasSize(sessionCount);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Property 6: Activity Recording Resets Inactivity Timer
    // For any session in the ConnectionRegistry, when an application-level message
    // is sent or received, the lastActivityAt timestamp shall be updated to the
    // current time, making the session no longer inactive regardless of its
    // previous lastActivityAt value.
    //
    // Validates: Requirements 4.5
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Property 6: Activity Recording Resets Timer — Register sessions with stale
     * activity timestamps, call recordActivity(), verify they are no longer in
     * the inactive list for a short timeout.
     *
     * Validates: Requirements 4.5
     */
    @Property(tries = 100)
    @Tag("Feature: websocket-resilience, Property 6: Activity Recording Resets Timer")
    void recordActivityResetsMakingSessionActive(
            @ForAll @IntRange(min = 1, max = 20) int sessionCount,
            @ForAll @IntRange(min = 10, max = 50) int timeoutMs) throws InterruptedException {

        InMemoryConnectionRegistry registry = createRegistry(1000);

        // Register sessions
        for (int i = 0; i < sessionCount; i++) {
            registry.register("stomp-" + i, "app-" + i);
        }

        // Wait for sessions to become stale (exceed the timeout)
        Thread.sleep(timeoutMs + 20);

        Duration timeout = Duration.ofMillis(timeoutMs);

        // Verify sessions are indeed inactive before recording activity
        List<String> inactiveBefore = registry.getInactiveSessions(timeout);
        assertThat(inactiveBefore)
                .as("Sessions should be inactive before recordActivity()")
                .hasSize(sessionCount);

        // Record activity on all sessions
        for (int i = 0; i < sessionCount; i++) {
            registry.recordActivity("stomp-" + i);
        }

        // After recording activity, sessions should no longer be inactive
        List<String> inactiveAfter = registry.getInactiveSessions(timeout);
        assertThat(inactiveAfter)
                .as("Sessions should be active (not in inactive list) after recordActivity()")
                .isEmpty();
    }

    /**
     * Property 6 (continued): Recording activity on a subset of sessions makes
     * only those sessions active; the rest remain inactive.
     *
     * Validates: Requirements 4.5
     */
    @Property(tries = 100)
    @Tag("Feature: websocket-resilience, Property 6: Activity Recording Resets Timer")
    void recordActivityOnSubsetOnlyResetsThoseSessions(
            @ForAll @IntRange(min = 2, max = 20) int totalSessions,
            @ForAll @IntRange(min = 10, max = 50) int timeoutMs) throws InterruptedException {

        InMemoryConnectionRegistry registry = createRegistry(1000);

        // Register all sessions
        for (int i = 0; i < totalSessions; i++) {
            registry.register("stomp-" + i, "app-" + i);
        }

        // Wait for all sessions to become stale
        Thread.sleep(timeoutMs + 20);

        Duration timeout = Duration.ofMillis(timeoutMs);

        // Record activity only on the first half
        int activeCount = totalSessions / 2;
        for (int i = 0; i < activeCount; i++) {
            registry.recordActivity("stomp-" + i);
        }

        // Verify: only sessions without recorded activity remain inactive
        List<String> inactiveAfter = registry.getInactiveSessions(timeout);
        int expectedInactive = totalSessions - activeCount;
        assertThat(inactiveAfter)
                .as("Only sessions without recorded activity should remain inactive")
                .hasSize(expectedInactive);

        // Verify none of the active sessions are in the inactive list
        for (int i = 0; i < activeCount; i++) {
            assertThat(inactiveAfter)
                    .as("Session stomp-%d had activity recorded, should not be inactive", i)
                    .doesNotContain("stomp-" + i);
        }
    }
}

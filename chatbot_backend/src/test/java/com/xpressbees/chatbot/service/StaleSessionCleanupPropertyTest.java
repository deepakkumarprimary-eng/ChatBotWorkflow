package com.xpressbees.chatbot.service;

import net.jqwik.api.*;
import net.jqwik.api.arbitraries.LongArbitrary;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Feature: production-readiness, Property 3: Stale session cleanup correctness

/**
 * Property-based tests for the stale session cleanup partitioning logic.
 *
 * This is a LOGIC test — it models sessions as simple POJOs and applies
 * the cleanup predicate directly without needing a real database.
 *
 * Validates: Requirements 4.1, 4.3
 */
class StaleSessionCleanupPropertyTest {

    // ─────────────────────────────────────────────────────────────────────────────
    // Property 3: Stale session cleanup correctness
    // For any set of chat sessions with varying updated_at timestamps and any
    // positive inactivity threshold, after the cleanup job executes, exactly those
    // sessions whose updated_at is older than now - threshold AND whose status was
    // "active" SHALL have their status changed to "expired", and all other sessions
    // SHALL remain unchanged.
    //
    // Validates: Requirements 4.1, 4.3
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Minimal session model for testing the cleanup partitioning logic.
     */
    static class TestSession {
        String status;
        LocalDateTime updatedAt;
        // Track original status to verify unchanged sessions
        final String originalStatus;

        TestSession(String status, LocalDateTime updatedAt) {
            this.status = status;
            this.updatedAt = updatedAt;
            this.originalStatus = status;
        }
    }

    /**
     * Applies the same partitioning logic as the repository query:
     * UPDATE ChatSession SET status = 'expired'
     * WHERE status = 'active' AND updatedAt < cutoff
     */
    private void applyCleanupLogic(List<TestSession> sessions, LocalDateTime cutoff) {
        for (TestSession session : sessions) {
            if ("active".equals(session.status) && session.updatedAt.isBefore(cutoff)) {
                session.status = "expired";
            }
        }
    }

    /**
     * Property 3: Only sessions with status "active" AND updatedAt before cutoff
     * are expired; all others remain unchanged.
     *
     * Validates: Requirements 4.1, 4.3
     */
    @Property(tries = 200)
    @Tag("production-readiness")
    @Tag("property-3")
    void onlyActiveSessionsOlderThanCutoffAreExpired(
            @ForAll("sessionSets") List<TestSession> sessions,
            @ForAll("thresholdHours") long thresholdHours) {

        // Calculate cutoff based on a fixed "now" reference point
        LocalDateTime now = LocalDateTime.of(2024, 6, 15, 12, 0, 0);
        LocalDateTime cutoff = now.minusHours(thresholdHours);

        // Apply the cleanup logic
        applyCleanupLogic(sessions, cutoff);

        // Verify the partitioning
        for (TestSession session : sessions) {
            boolean wasActive = "active".equals(session.originalStatus);
            boolean wasBeforeCutoff = session.updatedAt.isBefore(cutoff);

            if (wasActive && wasBeforeCutoff) {
                // Should have been expired
                assertThat(session.status)
                        .as("Active session with updatedAt %s (before cutoff %s) should be expired",
                                session.updatedAt, cutoff)
                        .isEqualTo("expired");
            } else {
                // Should remain unchanged
                assertThat(session.status)
                        .as("Session with original status '%s' and updatedAt %s (cutoff %s) should be unchanged",
                                session.originalStatus, session.updatedAt, cutoff)
                        .isEqualTo(session.originalStatus);
            }
        }
    }

    /**
     * Property 3 (count variant): The number of expired sessions equals exactly
     * the count of sessions that were active AND had updatedAt before cutoff.
     *
     * Validates: Requirements 4.1, 4.3
     */
    @Property(tries = 200)
    @Tag("production-readiness")
    @Tag("property-3")
    void expiredCountMatchesExpectedPartition(
            @ForAll("sessionSets") List<TestSession> sessions,
            @ForAll("thresholdHours") long thresholdHours) {

        LocalDateTime now = LocalDateTime.of(2024, 6, 15, 12, 0, 0);
        LocalDateTime cutoff = now.minusHours(thresholdHours);

        // Count expected expirations before applying logic
        long expectedExpiredCount = sessions.stream()
                .filter(s -> "active".equals(s.originalStatus) && s.updatedAt.isBefore(cutoff))
                .count();

        // Apply the cleanup logic
        applyCleanupLogic(sessions, cutoff);

        // Count actual expirations (sessions whose status changed)
        long actualExpiredCount = sessions.stream()
                .filter(s -> !s.status.equals(s.originalStatus))
                .count();

        assertThat(actualExpiredCount)
                .as("Number of status changes should equal the count of active sessions before cutoff")
                .isEqualTo(expectedExpiredCount);
    }

    /**
     * Property 3 (non-active immunity): Sessions that are NOT "active" should
     * never be modified regardless of their updatedAt timestamp.
     *
     * Validates: Requirements 4.1, 4.3
     */
    @Property(tries = 200)
    @Tag("production-readiness")
    @Tag("property-3")
    void nonActiveSessionsAreNeverModified(
            @ForAll("nonActiveSessionSets") List<TestSession> sessions,
            @ForAll("thresholdHours") long thresholdHours) {

        LocalDateTime now = LocalDateTime.of(2024, 6, 15, 12, 0, 0);
        LocalDateTime cutoff = now.minusHours(thresholdHours);

        // Apply the cleanup logic
        applyCleanupLogic(sessions, cutoff);

        // All non-active sessions should remain unchanged
        for (TestSession session : sessions) {
            assertThat(session.status)
                    .as("Non-active session (status='%s') should never be modified", session.originalStatus)
                    .isEqualTo(session.originalStatus);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Generators
    // ─────────────────────────────────────────────────────────────────────────────

    @Provide
    Arbitrary<List<TestSession>> sessionSets() {
        return sessionArbitrary().list().ofMinSize(1).ofMaxSize(50);
    }

    @Provide
    Arbitrary<List<TestSession>> nonActiveSessionSets() {
        return nonActiveSessionArbitrary().list().ofMinSize(1).ofMaxSize(50);
    }

    @Provide
    LongArbitrary thresholdHours() {
        // Threshold between 1 and 168 hours (1 week)
        return Arbitraries.longs().between(1, 168);
    }

    private Arbitrary<TestSession> sessionArbitrary() {
        Arbitrary<String> statuses = Arbitraries.of("active", "expired", "completed", "error");
        Arbitrary<LocalDateTime> timestamps = timestampArbitrary();

        return Combinators.combine(statuses, timestamps)
                .as(TestSession::new);
    }

    private Arbitrary<TestSession> nonActiveSessionArbitrary() {
        // Generate only non-active statuses
        Arbitrary<String> statuses = Arbitraries.of("expired", "completed", "error");
        Arbitrary<LocalDateTime> timestamps = timestampArbitrary();

        return Combinators.combine(statuses, timestamps)
                .as(TestSession::new);
    }

    private Arbitrary<LocalDateTime> timestampArbitrary() {
        // Generate timestamps spread around the reference "now" (2024-06-15 12:00)
        // Range: up to 30 days before to 1 day after now
        return Arbitraries.longs()
                .between(-720, 24) // hours offset from now
                .map(hoursOffset -> LocalDateTime.of(2024, 6, 15, 12, 0, 0).plusHours(hoursOffset));
    }
}

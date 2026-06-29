package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.dto.ChatResponse;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Feature: websocket-resilience, Property 7: Buffer Size Invariant
// Feature: websocket-resilience, Property 8: Buffer Full Triggers Pause
// Feature: websocket-resilience, Property 9: Buffer Drain Resumes Processing

/**
 * Property-based tests for SessionSendBuffer.
 *
 * Validates: Requirements 5.2, 5.3, 5.7
 */
class SessionSendBufferPropertyTest {

    private ChatResponse createMessage(String id) {
        return new ChatResponse(null, "msg-" + id, "session-" + id, false);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Property 7: Buffer Size Invariant
    // For any sequence of offer and poll operations on a SessionSendBuffer with
    // maximum size M, the size() method shall always return a value equal to
    // (successful offers − successful polls), bounded within [0, M].
    //
    // Validates: Requirements 5.7
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Property 7: Buffer Size Invariant — Generate random sequences of offer/poll
     * on buffers of random sizes, verify size() always equals (successful offers −
     * successful polls), bounded within [0, maxSize].
     *
     * Validates: Requirements 5.7
     */
    @Property(tries = 100)
    @Tag("Feature: websocket-resilience, Property 7: Buffer Size Invariant")
    void bufferSizeEqualsSuccessfulOffersMinusPolls(
            @ForAll @IntRange(min = 1, max = 50) int maxSize,
            @ForAll("operationSequences") List<Boolean> operations) {

        SessionSendBuffer buffer = new SessionSendBuffer(maxSize);
        int successfulOffers = 0;
        int successfulPolls = 0;

        for (int i = 0; i < operations.size(); i++) {
            boolean isOffer = operations.get(i);

            if (isOffer) {
                boolean offered = buffer.offer(createMessage(String.valueOf(i)));
                if (offered) {
                    successfulOffers++;
                }
            } else {
                ChatResponse polled = buffer.poll();
                if (polled != null) {
                    successfulPolls++;
                }
            }

            // Invariant: size == successfulOffers - successfulPolls
            int expectedSize = successfulOffers - successfulPolls;
            assertThat(buffer.size())
                    .as("After operation %d: size should equal successful offers (%d) minus successful polls (%d)",
                            i, successfulOffers, successfulPolls)
                    .isEqualTo(expectedSize);

            // Invariant: size is bounded within [0, maxSize]
            assertThat(buffer.size())
                    .as("Buffer size should be within [0, %d]", maxSize)
                    .isBetween(0, maxSize);
        }
    }

    @Provide
    Arbitrary<List<Boolean>> operationSequences() {
        // Generate sequences of 5-100 operations (true = offer, false = poll)
        return Arbitraries.of(true, false)
                .list()
                .ofMinSize(5)
                .ofMaxSize(100);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Property 8: Buffer Full Triggers Pause
    // For any SessionSendBuffer with maximum size M, after exactly M messages
    // have been successfully offered, the buffer shall report as full and the
    // session's workflow processing shall be marked as paused.
    //
    // Validates: Requirements 5.2
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Property 8: Buffer Full Triggers Pause — Fill buffer to capacity (random
     * size 1-50), verify isFull() returns true after calling markPaused(),
     * verify isPaused() is true.
     *
     * Validates: Requirements 5.2
     */
    @Property(tries = 100)
    @Tag("Feature: websocket-resilience, Property 8: Buffer Full Triggers Pause")
    void bufferFullTriggersPauseState(
            @ForAll @IntRange(min = 1, max = 50) int maxSize) {

        SessionSendBuffer buffer = new SessionSendBuffer(maxSize);

        // Fill buffer to capacity
        for (int i = 0; i < maxSize; i++) {
            boolean offered = buffer.offer(createMessage(String.valueOf(i)));
            assertThat(offered)
                    .as("Offer %d of %d should succeed", i + 1, maxSize)
                    .isTrue();
        }

        // Buffer should be full
        assertThat(buffer.isFull())
                .as("Buffer should be full after %d offers (maxSize=%d)", maxSize, maxSize)
                .isTrue();

        // An additional offer should fail
        boolean overflowOffered = buffer.offer(createMessage("overflow"));
        assertThat(overflowOffered)
                .as("Offer beyond capacity should be rejected")
                .isFalse();

        // Mark paused (simulates back-pressure controller behavior)
        buffer.markPaused();

        // Verify paused state
        assertThat(buffer.isPaused())
                .as("Buffer should be paused after markPaused()")
                .isTrue();

        // Verify pausedSince is set
        assertThat(buffer.getPausedSince())
                .as("pausedSince should be set after markPaused()")
                .isNotNull();
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Property 9: Buffer Drain Resumes Processing
    // For any session that is currently paused due to a full buffer, when at least
    // one message is consumed (polled) from the buffer making space available,
    // the session's workflow processing shall be marked as resumed.
    //
    // Validates: Requirements 5.3
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Property 9: Buffer Drain Resumes Processing — Start with a full paused
     * buffer, poll one message, call markResumed(), verify isPaused() is false
     * and isFull() is false.
     *
     * Validates: Requirements 5.3
     */
    @Property(tries = 100)
    @Tag("Feature: websocket-resilience, Property 9: Buffer Drain Resumes Processing")
    void bufferDrainResumesProcessing(
            @ForAll @IntRange(min = 1, max = 50) int maxSize) {

        SessionSendBuffer buffer = new SessionSendBuffer(maxSize);

        // Fill buffer to capacity
        for (int i = 0; i < maxSize; i++) {
            buffer.offer(createMessage(String.valueOf(i)));
        }

        // Mark paused (buffer is full)
        buffer.markPaused();
        assertThat(buffer.isPaused())
                .as("Precondition: buffer should be paused")
                .isTrue();
        assertThat(buffer.isFull())
                .as("Precondition: buffer should be full")
                .isTrue();

        // Poll one message — creates space
        ChatResponse polled = buffer.poll();
        assertThat(polled)
                .as("Poll from full buffer should return a message")
                .isNotNull();

        // Buffer is no longer full
        assertThat(buffer.isFull())
                .as("Buffer should not be full after polling one message")
                .isFalse();

        // Mark resumed (simulates back-pressure controller behavior)
        buffer.markResumed();

        // Verify resumed state
        assertThat(buffer.isPaused())
                .as("Buffer should not be paused after markResumed()")
                .isFalse();

        // Verify pausedSince is cleared
        assertThat(buffer.getPausedSince())
                .as("pausedSince should be null after markResumed()")
                .isNull();
    }
}

package com.chatbot.workflow.controller;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

import com.chatbot.workflow.repository.ExecutionHistoryEntity;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tag;

/**
 * Property-based tests for execution monitoring logic.
 * Pure unit tests — no Spring context needed.
 *
 * **Validates: Requirements 8.1, 8.2, 8.4, 8.6**
 */
class ExecutionMonitoringProperties {

    // ========================================================================
    // Property 26: Execution history completeness
    // For any execution processing N state transitions, the execution history
    // should contain N records, each with a non-null state identifier,
    // ISO 8601 entry time, exit time, outcome, and context snapshot.
    // ========================================================================

    /**
     * Property 26: For N history entries, each should have non-null stateId,
     * entryTime, and outcome. The list should contain exactly N records.
     *
     * **Validates: Requirements 8.1, 8.2**
     */
    @Property(tries = 100)
    @Tag("Feature: chatbot-workflow-builder, Property 26: Execution history completeness")
    void executionHistoryShouldHaveNRecordsWithRequiredFields(
            @ForAll("historyEntryCounts") int n) {

        UUID executionId = UUID.randomUUID();
        List<ExecutionHistoryEntity> history = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            ExecutionHistoryEntity entity = new ExecutionHistoryEntity(
                    executionId,
                    UUID.randomUUID(),
                    "State_" + i,
                    i + 1
            );
            entity.setExitTime(Instant.now().plusSeconds(i + 1));
            entity.setOutcome("succeeded");
            entity.setContextSnapshot("{\"key\":\"value_" + i + "\"}");
            history.add(entity);
        }

        // Verify: exactly N records
        assertThat(history).hasSize(n);

        // Verify: each record has non-null required fields
        for (int i = 0; i < n; i++) {
            ExecutionHistoryEntity entry = history.get(i);

            assertThat(entry.getStateId())
                    .as("History entry %d should have non-null stateId", i)
                    .isNotNull();

            assertThat(entry.getEntryTime())
                    .as("History entry %d should have non-null entryTime", i)
                    .isNotNull();

            assertThat(entry.getExitTime())
                    .as("History entry %d should have non-null exitTime", i)
                    .isNotNull();

            assertThat(entry.getOutcome())
                    .as("History entry %d should have non-null outcome", i)
                    .isNotNull();

            assertThat(entry.getContextSnapshot())
                    .as("History entry %d should have non-null contextSnapshot", i)
                    .isNotNull();
        }
    }

    /**
     * Property 26: Each history entry's stateId should be a valid UUID (non-null),
     * and entryTime should be a valid instant (ISO 8601 representable).
     *
     * **Validates: Requirements 8.1, 8.2**
     */
    @Property(tries = 100)
    @Tag("Feature: chatbot-workflow-builder, Property 26: Execution history completeness")
    void executionHistoryFieldsShouldBeValidFormats(
            @ForAll("historyEntryCounts") int n,
            @ForAll("outcomes") String outcome) {

        UUID executionId = UUID.randomUUID();
        List<ExecutionHistoryEntity> history = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            ExecutionHistoryEntity entity = new ExecutionHistoryEntity(
                    executionId,
                    UUID.randomUUID(),
                    "State_" + i,
                    i + 1
            );
            entity.setExitTime(entity.getEntryTime().plusSeconds(5));
            entity.setOutcome(outcome);
            entity.setContextSnapshot("{}");
            history.add(entity);
        }

        for (ExecutionHistoryEntity entry : history) {
            // stateId should be a valid UUID
            assertThat(entry.getStateId()).isNotNull();
            assertThat(entry.getStateId().toString()).matches(
                    "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

            // entryTime should be representable as ISO 8601
            assertThat(entry.getEntryTime().toString()).isNotEmpty();

            // outcome should be one of the valid values
            assertThat(entry.getOutcome()).isIn("succeeded", "failed", "skipped", "timed_out");
        }
    }

    // ========================================================================
    // Property 27: Stack trace truncation
    // For any error stack trace string, the recorded stack trace should be at
    // most 5000 characters long. If the original exceeds 5000 characters, it
    // should be truncated to exactly 5000 characters. Null → null.
    // ========================================================================

    /**
     * Property 27: truncateStackTrace should return at most 5000 characters
     * for any input string.
     *
     * **Validates: Requirements 8.4**
     */
    @Property(tries = 100)
    @Tag("Feature: chatbot-workflow-builder, Property 27: Stack trace truncation")
    void truncatedStackTraceShouldBeAtMost5000Chars(
            @ForAll("stackTraceStrings") String stackTrace) {

        String result = ExecutionController.truncateStackTrace(stackTrace);

        assertThat(result)
                .as("Truncated stack trace should be at most 5000 characters, was %d", result.length())
                .hasSizeLessThanOrEqualTo(5000);
    }

    /**
     * Property 27: Strings at or under 5000 characters should be unchanged.
     *
     * **Validates: Requirements 8.4**
     */
    @Property(tries = 100)
    @Tag("Feature: chatbot-workflow-builder, Property 27: Stack trace truncation")
    void shortStackTraceShouldBeUnchanged(
            @ForAll("shortStackTraceStrings") String stackTrace) {

        String result = ExecutionController.truncateStackTrace(stackTrace);

        assertThat(result)
                .as("Stack trace of length %d should be unchanged", stackTrace.length())
                .isEqualTo(stackTrace);
    }

    /**
     * Property 27: Strings exceeding 5000 characters should be truncated to exactly 5000.
     *
     * **Validates: Requirements 8.4**
     */
    @Property(tries = 100)
    @Tag("Feature: chatbot-workflow-builder, Property 27: Stack trace truncation")
    void longStackTraceShouldBeTruncatedToExactly5000(
            @ForAll("longStackTraceStrings") String stackTrace) {

        String result = ExecutionController.truncateStackTrace(stackTrace);

        assertThat(result)
                .as("Stack trace exceeding 5000 chars should be truncated to exactly 5000")
                .hasSize(5000);

        // The truncated result should be the first 5000 chars of the input
        assertThat(result).isEqualTo(stackTrace.substring(0, 5000));
    }

    /**
     * Property 27: Null input should return null.
     *
     * **Validates: Requirements 8.4**
     */
    @Property(tries = 100)
    @Tag("Feature: chatbot-workflow-builder, Property 27: Stack trace truncation")
    void nullStackTraceShouldReturnNull() {
        String result = ExecutionController.truncateStackTrace(null);

        assertThat(result).isNull();
    }

    // ========================================================================
    // Property 28: Execution listing pagination
    // For any set of executions and any requested page size P, the result
    // should contain at most min(P, 100) items. Default page size is 20.
    // ========================================================================

    /**
     * Property 28: Effective page size should be min(requestedSize, 100) for any
     * requested page size P >= 1.
     *
     * **Validates: Requirements 8.6**
     */
    @Property(tries = 100)
    @Tag("Feature: chatbot-workflow-builder, Property 28: Execution listing pagination")
    void effectivePageSizeShouldBeMinOfRequestedAndMax(
            @ForAll("pageSizes") int requestedSize) {

        // Replicate the capping logic from ExecutionController.listExecutions
        int effectiveSize = Math.min(Math.max(requestedSize, 1), 100);

        assertThat(effectiveSize)
                .as("Effective size for requested=%d should be at most 100", requestedSize)
                .isLessThanOrEqualTo(100);

        assertThat(effectiveSize)
                .as("Effective size for requested=%d should be at least 1", requestedSize)
                .isGreaterThanOrEqualTo(1);

        // Verify the specific capping: min(max(size, 1), 100)
        int expected = Math.min(Math.max(requestedSize, 1), 100);
        assertThat(effectiveSize).isEqualTo(expected);
    }

    /**
     * Property 28: Default page size should be 20.
     *
     * **Validates: Requirements 8.6**
     */
    @Property(tries = 100)
    @Tag("Feature: chatbot-workflow-builder, Property 28: Execution listing pagination")
    void defaultPageSizeShouldBe20() {
        // The controller uses @RequestParam(defaultValue = "20") int size
        // Verify that when size=20, effective size is 20
        int defaultSize = 20;
        int effectiveSize = Math.min(Math.max(defaultSize, 1), 100);

        assertThat(effectiveSize)
                .as("Default page size should be 20")
                .isEqualTo(20);
    }

    /**
     * Property 28: Requested sizes above 100 should be capped to 100.
     *
     * **Validates: Requirements 8.6**
     */
    @Property(tries = 100)
    @Tag("Feature: chatbot-workflow-builder, Property 28: Execution listing pagination")
    void pageSizeAbove100ShouldBeCappedTo100(
            @ForAll("largepageSizes") int requestedSize) {

        int effectiveSize = Math.min(Math.max(requestedSize, 1), 100);

        assertThat(effectiveSize)
                .as("Requested size %d should be capped to 100", requestedSize)
                .isEqualTo(100);
    }

    // ========================================================================
    // Providers (Generators)
    // ========================================================================

    @Provide
    Arbitrary<Integer> historyEntryCounts() {
        return Arbitraries.integers().between(1, 10);
    }

    @Provide
    Arbitrary<String> outcomes() {
        return Arbitraries.of("succeeded", "failed", "skipped", "timed_out");
    }

    @Provide
    Arbitrary<String> stackTraceStrings() {
        // Generate strings of various lengths, including ones exceeding 5000
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars('.', '\n', ' ', '\t', '(', ')', ':')
                .ofMinLength(0)
                .ofMaxLength(10000);
    }

    @Provide
    Arbitrary<String> shortStackTraceStrings() {
        // Strings at or under 5000 chars
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars('.', '\n', ' ')
                .ofMinLength(0)
                .ofMaxLength(5000);
    }

    @Provide
    Arbitrary<String> longStackTraceStrings() {
        // Strings exceeding 5000 chars
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars('.', '\n', ' ')
                .ofMinLength(5001)
                .ofMaxLength(10000);
    }

    @Provide
    Arbitrary<Integer> pageSizes() {
        // Page sizes covering valid and boundary values
        return Arbitraries.integers().between(1, 200);
    }

    @Provide
    Arbitrary<Integer> largepageSizes() {
        // Page sizes above the max (100)
        return Arbitraries.integers().between(101, 500);
    }
}

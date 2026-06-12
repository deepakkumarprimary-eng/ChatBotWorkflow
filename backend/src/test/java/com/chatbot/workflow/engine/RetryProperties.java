package com.chatbot.workflow.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.mockito.Mockito;

import com.chatbot.workflow.model.RetryPolicy;
import com.chatbot.workflow.model.StateDefinition;
import com.chatbot.workflow.model.StateType;
import com.chatbot.workflow.repository.RetryAttemptEntity;
import com.chatbot.workflow.repository.RetryAttemptRepository;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tag;

/**
 * Property-based tests for RetryManager logic.
 * Pure unit tests — no Spring context needed. Direct instantiation with mock repository and no-op sleeper.
 *
 * **Validates: Requirements 9.1, 9.2, 9.5**
 */
class RetryProperties {

    // No-op sleeper that does nothing — avoids actual delays in tests
    private static final Sleeper NO_OP_SLEEPER = millis -> { };

    // ========================================================================
    // Property 29: Retry policy range validation
    // For any pair of integers (maxRetries, backoffInterval), it should be
    // accepted as a retry policy if and only if maxRetries is in [0, 10] and
    // backoffInterval is in [1, 300] seconds.
    // ========================================================================

    /**
     * Property 29: Valid retry policies (maxRetries in [0,10], backoffInterval in [1,300])
     * should be accepted by validateRetryPolicy.
     *
     * **Validates: Requirements 9.1**
     */
    @Property(tries = 100)
    @Tag("Feature: chatbot-workflow-builder, Property 29: Retry policy range validation")
    void validRetryPolicyShouldBeAccepted(
            @ForAll("validMaxRetries") int maxRetries,
            @ForAll("validBackoffIntervals") int backoffInterval) {

        RetryAttemptRepository mockRepo = Mockito.mock(RetryAttemptRepository.class);
        RetryManager retryManager = new RetryManager(mockRepo, NO_OP_SLEEPER);

        RetryPolicy policy = new RetryPolicy(maxRetries, backoffInterval);
        boolean result = retryManager.validateRetryPolicy(policy);

        assertThat(result)
                .as("Policy(maxRetries=%d, backoff=%d) should be valid", maxRetries, backoffInterval)
                .isTrue();
    }

    /**
     * Property 29: Invalid retry policies (maxRetries outside [0,10] OR backoffInterval
     * outside [1,300]) should be rejected by validateRetryPolicy.
     *
     * **Validates: Requirements 9.1**
     */
    @Property(tries = 100)
    @Tag("Feature: chatbot-workflow-builder, Property 29: Retry policy range validation")
    void invalidRetryPolicyShouldBeRejected(
            @ForAll("invalidRetryPolicyPairs") int[] pair) {

        int maxRetries = pair[0];
        int backoffInterval = pair[1];

        RetryAttemptRepository mockRepo = Mockito.mock(RetryAttemptRepository.class);
        RetryManager retryManager = new RetryManager(mockRepo, NO_OP_SLEEPER);

        RetryPolicy policy = new RetryPolicy(maxRetries, backoffInterval);
        boolean result = retryManager.validateRetryPolicy(policy);

        assertThat(result)
                .as("Policy(maxRetries=%d, backoff=%d) should be invalid", maxRetries, backoffInterval)
                .isFalse();
    }

    /**
     * Property 29: Null retry policy should be rejected.
     *
     * **Validates: Requirements 9.1**
     */
    @Property(tries = 100)
    @Tag("Feature: chatbot-workflow-builder, Property 29: Retry policy range validation")
    void nullRetryPolicyShouldBeRejected() {
        RetryAttemptRepository mockRepo = Mockito.mock(RetryAttemptRepository.class);
        RetryManager retryManager = new RetryManager(mockRepo, NO_OP_SLEEPER);

        boolean result = retryManager.validateRetryPolicy(null);

        assertThat(result).isFalse();
    }

    // ========================================================================
    // Property 30: Exponential backoff calculation
    // For any retry policy with baseInterval B and maxRetries N, when a state
    // fails K times (K ≤ N), the delay before attempt K should be
    // B × 2^(K-1) seconds (returned as milliseconds).
    // ========================================================================

    /**
     * Property 30: calculateDelay should return baseInterval × 2^(attemptNumber-1) × 1000 ms.
     *
     * **Validates: Requirements 9.2**
     */
    @Property(tries = 100)
    @Tag("Feature: chatbot-workflow-builder, Property 30: Exponential backoff calculation")
    void calculateDelayShouldFollowExponentialBackoff(
            @ForAll("validBackoffIntervals") int baseInterval,
            @ForAll("attemptNumbers") int attemptNumber) {

        RetryAttemptRepository mockRepo = Mockito.mock(RetryAttemptRepository.class);
        RetryManager retryManager = new RetryManager(mockRepo, NO_OP_SLEEPER);

        long result = retryManager.calculateDelay(baseInterval, attemptNumber);

        // Expected: baseInterval × 2^(attemptNumber - 1) × 1000
        long expectedDelayMs = (long) baseInterval * (1L << (attemptNumber - 1)) * 1000L;

        assertThat(result)
                .as("Delay for base=%d, attempt=%d should be %d ms", baseInterval, attemptNumber, expectedDelayMs)
                .isEqualTo(expectedDelayMs);
    }

    /**
     * Property 30: No more than maxRetries retry attempts should be made.
     * A processor that always fails should result in exactly maxRetries retry attempts.
     *
     * **Validates: Requirements 9.2**
     */
    @Property(tries = 100)
    @Tag("Feature: chatbot-workflow-builder, Property 30: Exponential backoff calculation")
    void noMoreThanMaxRetriesAttemptsShouldBeMade(
            @ForAll("retryCountsForExecution") int maxRetries) {

        RetryAttemptRepository mockRepo = Mockito.mock(RetryAttemptRepository.class);
        Mockito.when(mockRepo.save(Mockito.any(RetryAttemptEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RetryManager retryManager = new RetryManager(mockRepo, NO_OP_SLEEPER);

        // Create a processor that always fails
        StateProcessor alwaysFailsProcessor = createAlwaysFailingProcessor("simulated failure");

        RetryPolicy policy = new RetryPolicy(maxRetries, 1); // backoffInterval=1 to keep delays minimal
        StateDefinition state = new StateDefinition(
                UUID.randomUUID(), StateType.API_CALL, "FailingState", null,
                Collections.emptyMap(), policy, null);
        ExecutionContext context = new ExecutionContext(UUID.randomUUID(), new HashMap<>());

        retryManager.executeWithRetry(alwaysFailsProcessor, state, context);

        // Verify that exactly maxRetries retry attempts were recorded
        Mockito.verify(mockRepo, Mockito.times(maxRetries)).save(Mockito.any(RetryAttemptEntity.class));
    }

    // ========================================================================
    // Property 31: Retry attempt recording
    // For any state that undergoes K retry attempts, the execution history
    // should contain K retry records, each with correct attempt_number
    // (1 through K), a non-null timestamp, and the error message.
    // ========================================================================

    /**
     * Property 31: Each retry attempt should be recorded with correct attempt_number,
     * non-null timestamp, and the error message that triggered the retry.
     *
     * **Validates: Requirements 9.5**
     */
    @Property(tries = 100)
    @Tag("Feature: chatbot-workflow-builder, Property 31: Retry attempt recording")
    void retryAttemptsShouldBeRecordedCorrectly(
            @ForAll("retryCountsForExecution") int maxRetries,
            @ForAll("errorMessages") String errorMessage) {

        // Capture all saved retry attempt entities
        List<RetryAttemptEntity> capturedEntities = new ArrayList<>();
        RetryAttemptRepository mockRepo = Mockito.mock(RetryAttemptRepository.class);
        Mockito.when(mockRepo.save(Mockito.any(RetryAttemptEntity.class)))
                .thenAnswer(invocation -> {
                    RetryAttemptEntity entity = invocation.getArgument(0);
                    // Simulate @PrePersist (set attemptedAt if null)
                    if (entity.getAttemptedAt() == null) {
                        entity.setAttemptedAt(java.time.Instant.now());
                    }
                    capturedEntities.add(entity);
                    return entity;
                });

        RetryManager retryManager = new RetryManager(mockRepo, NO_OP_SLEEPER);

        // Create a processor that always fails with the given error message
        StateProcessor alwaysFailsProcessor = createAlwaysFailingProcessor(errorMessage);

        RetryPolicy policy = new RetryPolicy(maxRetries, 1);
        UUID stateId = UUID.randomUUID();
        StateDefinition state = new StateDefinition(
                stateId, StateType.API_CALL, "FailingState", null,
                Collections.emptyMap(), policy, null);
        UUID executionId = UUID.randomUUID();
        ExecutionContext context = new ExecutionContext(executionId, new HashMap<>());

        retryManager.executeWithRetry(alwaysFailsProcessor, state, context);

        // Verify K retry records were captured
        assertThat(capturedEntities).hasSize(maxRetries);

        // Verify each record has correct attempt_number (1 through K),
        // non-null timestamp, and the error message
        for (int i = 0; i < maxRetries; i++) {
            RetryAttemptEntity entity = capturedEntities.get(i);

            assertThat(entity.getAttemptNumber())
                    .as("Attempt number for retry %d should be %d", i, i + 1)
                    .isEqualTo(i + 1);

            assertThat(entity.getAttemptedAt())
                    .as("Timestamp for retry %d should not be null", i + 1)
                    .isNotNull();

            assertThat(entity.getErrorMessage())
                    .as("Error message for retry %d should match", i + 1)
                    .isEqualTo(errorMessage);

            assertThat(entity.getExecutionId())
                    .as("Execution ID for retry %d should match", i + 1)
                    .isEqualTo(executionId);

            assertThat(entity.getStateId())
                    .as("State ID for retry %d should match", i + 1)
                    .isEqualTo(stateId);
        }
    }

    // ========================================================================
    // Helper: Create a StateProcessor that always returns a failure
    // ========================================================================

    private StateProcessor createAlwaysFailingProcessor(String errorMessage) {
        StateProcessor processor = Mockito.mock(StateProcessor.class);
        Mockito.when(processor.getType()).thenReturn(StateType.API_CALL);
        Mockito.when(processor.process(Mockito.any(StateDefinition.class), Mockito.any(ExecutionContext.class)))
                .thenReturn(StateProcessorResult.failure(errorMessage));
        return processor;
    }

    // ========================================================================
    // Providers (Generators)
    // ========================================================================

    @Provide
    Arbitrary<Integer> validMaxRetries() {
        return Arbitraries.integers().between(0, 10);
    }

    @Provide
    Arbitrary<Integer> validBackoffIntervals() {
        return Arbitraries.integers().between(1, 300);
    }

    @Provide
    Arbitrary<int[]> invalidRetryPolicyPairs() {
        // Generate pairs where at least one value is out of range
        Arbitrary<int[]> invalidMaxRetries = Combinators.combine(
                Arbitraries.oneOf(
                        Arbitraries.integers().between(-100, -1),
                        Arbitraries.integers().between(11, 200)
                ),
                Arbitraries.integers().between(1, 300)
        ).as((mr, bi) -> new int[]{mr, bi});

        Arbitrary<int[]> invalidBackoff = Combinators.combine(
                Arbitraries.integers().between(0, 10),
                Arbitraries.oneOf(
                        Arbitraries.integers().between(-100, 0),
                        Arbitraries.integers().between(301, 1000)
                )
        ).as((mr, bi) -> new int[]{mr, bi});

        Arbitrary<int[]> bothInvalid = Combinators.combine(
                Arbitraries.oneOf(
                        Arbitraries.integers().between(-100, -1),
                        Arbitraries.integers().between(11, 200)
                ),
                Arbitraries.oneOf(
                        Arbitraries.integers().between(-100, 0),
                        Arbitraries.integers().between(301, 1000)
                )
        ).as((mr, bi) -> new int[]{mr, bi});

        return Arbitraries.oneOf(invalidMaxRetries, invalidBackoff, bothInvalid);
    }

    @Provide
    Arbitrary<Integer> attemptNumbers() {
        return Arbitraries.integers().between(1, 10);
    }

    @Provide
    Arbitrary<Integer> retryCountsForExecution() {
        // For executeWithRetry tests, use 1-5 to keep tests fast
        return Arbitraries.integers().between(1, 5);
    }

    @Provide
    Arbitrary<String> errorMessages() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars(' ', '_', '-', ':', '.')
                .ofMinLength(5)
                .ofMaxLength(50);
    }
}

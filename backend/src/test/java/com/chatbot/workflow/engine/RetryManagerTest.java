package com.chatbot.workflow.engine;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chatbot.workflow.model.RetryPolicy;
import com.chatbot.workflow.model.StateDefinition;
import com.chatbot.workflow.model.StateOutcome;
import com.chatbot.workflow.model.StateType;
import com.chatbot.workflow.repository.RetryAttemptEntity;
import com.chatbot.workflow.repository.RetryAttemptRepository;

/**
 * Unit tests for RetryManager verifying:
 * - calculateDelay formula correctness for attempts 1-10
 * - shouldRetry boolean logic
 * - validateRetryPolicy accepts/rejects correctly
 * - recordAttempt persists correctly
 * - executeWithRetry retries on failure and succeeds on recovery
 * - executeWithRetry exhausts retries and returns failure
 */
class RetryManagerTest {

    private RetryManager retryManager;
    private RetryAttemptRepository retryAttemptRepository;
    private Sleeper sleeper;

    @BeforeEach
    void setUp() {
        retryAttemptRepository = mock(RetryAttemptRepository.class);
        // No-op sleeper for tests
        sleeper = millis -> { /* no-op */ };
        retryManager = new RetryManager(retryAttemptRepository, sleeper);
    }

    // --- calculateDelay tests ---

    @Test
    void calculateDelay_attempt1_returnsBaseInterval() {
        // delay = 5 × 2^(1-1) = 5 × 1 = 5 seconds = 5000ms
        assertEquals(5000L, retryManager.calculateDelay(5, 1));
    }

    @Test
    void calculateDelay_attempt2_doublesBaseInterval() {
        // delay = 5 × 2^(2-1) = 5 × 2 = 10 seconds = 10000ms
        assertEquals(10000L, retryManager.calculateDelay(5, 2));
    }

    @Test
    void calculateDelay_attempt3_quadruplesBaseInterval() {
        // delay = 5 × 2^(3-1) = 5 × 4 = 20 seconds = 20000ms
        assertEquals(20000L, retryManager.calculateDelay(5, 3));
    }

    @Test
    void calculateDelay_formulaCorrectForAttempts1Through10() {
        int baseInterval = 3; // 3 seconds
        for (int attempt = 1; attempt <= 10; attempt++) {
            long expected = (long) baseInterval * (1L << (attempt - 1)) * 1000L;
            assertEquals(expected, retryManager.calculateDelay(baseInterval, attempt),
                    "Failed for attempt " + attempt);
        }
    }

    @Test
    void calculateDelay_baseInterval1_attempt10() {
        // delay = 1 × 2^9 = 512 seconds = 512000ms
        assertEquals(512000L, retryManager.calculateDelay(1, 10));
    }

    @Test
    void calculateDelay_baseInterval300_attempt1() {
        // delay = 300 × 2^0 = 300 seconds = 300000ms
        assertEquals(300000L, retryManager.calculateDelay(300, 1));
    }

    // --- shouldRetry tests ---

    @Test
    void shouldRetry_currentAttemptLessThanMaxRetries_returnsTrue() {
        RetryPolicy policy = new RetryPolicy(3, 5);
        assertTrue(retryManager.shouldRetry(policy, 0));
        assertTrue(retryManager.shouldRetry(policy, 1));
        assertTrue(retryManager.shouldRetry(policy, 2));
    }

    @Test
    void shouldRetry_currentAttemptEqualsMaxRetries_returnsFalse() {
        RetryPolicy policy = new RetryPolicy(3, 5);
        assertFalse(retryManager.shouldRetry(policy, 3));
    }

    @Test
    void shouldRetry_currentAttemptExceedsMaxRetries_returnsFalse() {
        RetryPolicy policy = new RetryPolicy(3, 5);
        assertFalse(retryManager.shouldRetry(policy, 4));
        assertFalse(retryManager.shouldRetry(policy, 100));
    }

    @Test
    void shouldRetry_maxRetriesZero_alwaysFalse() {
        RetryPolicy policy = new RetryPolicy(0, 5);
        assertFalse(retryManager.shouldRetry(policy, 0));
    }

    @Test
    void shouldRetry_nullPolicy_returnsFalse() {
        assertFalse(retryManager.shouldRetry(null, 0));
    }

    // --- validateRetryPolicy tests ---

    @Test
    void validateRetryPolicy_validPolicy_returnsTrue() {
        assertTrue(retryManager.validateRetryPolicy(new RetryPolicy(0, 1)));
        assertTrue(retryManager.validateRetryPolicy(new RetryPolicy(5, 150)));
        assertTrue(retryManager.validateRetryPolicy(new RetryPolicy(10, 300)));
    }

    @Test
    void validateRetryPolicy_boundaryValues_returnsTrue() {
        assertTrue(retryManager.validateRetryPolicy(new RetryPolicy(0, 1)));
        assertTrue(retryManager.validateRetryPolicy(new RetryPolicy(10, 300)));
    }

    @Test
    void validateRetryPolicy_maxRetriesTooHigh_returnsFalse() {
        assertFalse(retryManager.validateRetryPolicy(new RetryPolicy(11, 5)));
    }

    @Test
    void validateRetryPolicy_maxRetriesTooLow_returnsFalse() {
        assertFalse(retryManager.validateRetryPolicy(new RetryPolicy(-1, 5)));
    }

    @Test
    void validateRetryPolicy_backoffIntervalTooLow_returnsFalse() {
        assertFalse(retryManager.validateRetryPolicy(new RetryPolicy(3, 0)));
    }

    @Test
    void validateRetryPolicy_backoffIntervalTooHigh_returnsFalse() {
        assertFalse(retryManager.validateRetryPolicy(new RetryPolicy(3, 301)));
    }

    @Test
    void validateRetryPolicy_null_returnsFalse() {
        assertFalse(retryManager.validateRetryPolicy(null));
    }

    // --- recordAttempt tests ---

    @Test
    void recordAttempt_persistsCorrectly() {
        UUID executionId = UUID.randomUUID();
        UUID stateId = UUID.randomUUID();

        when(retryAttemptRepository.save(any(RetryAttemptEntity.class))).thenAnswer(invocation -> {
            RetryAttemptEntity entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });

        RetryAttemptEntity result = retryManager.recordAttempt(executionId, stateId, 2, "Connection timeout");

        assertNotNull(result);
        ArgumentCaptor<RetryAttemptEntity> captor = ArgumentCaptor.forClass(RetryAttemptEntity.class);
        verify(retryAttemptRepository).save(captor.capture());

        RetryAttemptEntity saved = captor.getValue();
        assertEquals(executionId, saved.getExecutionId());
        assertEquals(stateId, saved.getStateId());
        assertEquals(2, saved.getAttemptNumber());
        assertEquals("Connection timeout", saved.getErrorMessage());
    }

    // --- executeWithRetry tests ---

    @Test
    void executeWithRetry_successOnFirstAttempt_noRetries() {
        StateProcessor processor = mock(StateProcessor.class);
        UUID stateId = UUID.randomUUID();
        StateDefinition state = new StateDefinition(
                stateId, StateType.API_CALL, "TestState", null, null,
                new RetryPolicy(3, 5), null);
        ExecutionContext context = new ExecutionContext(UUID.randomUUID(), null);

        when(processor.process(state, context)).thenReturn(StateProcessorResult.success());

        StateProcessorResult result = retryManager.executeWithRetry(processor, state, context);

        assertEquals(StateOutcome.SUCCEEDED, result.getOutcome());
        verify(retryAttemptRepository, never()).save(any());
    }

    @Test
    void executeWithRetry_failsThenSucceeds_retriesAndRecovers() {
        StateProcessor processor = mock(StateProcessor.class);
        UUID stateId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        StateDefinition state = new StateDefinition(
                stateId, StateType.API_CALL, "TestState", null, null,
                new RetryPolicy(3, 2), null);
        ExecutionContext context = new ExecutionContext(executionId, null);

        // Fail first, fail second, then succeed on retry attempt 2
        when(processor.process(state, context))
                .thenReturn(StateProcessorResult.failure("Error 1"))
                .thenReturn(StateProcessorResult.failure("Error 2"))
                .thenReturn(StateProcessorResult.success());

        when(retryAttemptRepository.save(any(RetryAttemptEntity.class))).thenAnswer(invocation -> {
            RetryAttemptEntity entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });

        StateProcessorResult result = retryManager.executeWithRetry(processor, state, context);

        assertEquals(StateOutcome.SUCCEEDED, result.getOutcome());
        // 2 retry attempts recorded (attempt 1 and 2 of retry loop)
        verify(retryAttemptRepository, times(2)).save(any(RetryAttemptEntity.class));
    }

    @Test
    void executeWithRetry_exhaustsRetries_returnsFailure() {
        StateProcessor processor = mock(StateProcessor.class);
        UUID stateId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        StateDefinition state = new StateDefinition(
                stateId, StateType.API_CALL, "TestState", null, null,
                new RetryPolicy(2, 1), null);
        ExecutionContext context = new ExecutionContext(executionId, null);

        // Always fail
        when(processor.process(state, context))
                .thenReturn(StateProcessorResult.failure("Persistent error"));

        when(retryAttemptRepository.save(any(RetryAttemptEntity.class))).thenAnswer(invocation -> {
            RetryAttemptEntity entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });

        StateProcessorResult result = retryManager.executeWithRetry(processor, state, context);

        assertEquals(StateOutcome.FAILED, result.getOutcome());
        assertEquals("error", result.getNextTransitionCondition());
        assertNotNull(result.getErrorMessage());
        // maxRetries = 2, so 2 retry attempts
        verify(retryAttemptRepository, times(2)).save(any(RetryAttemptEntity.class));
        // Initial attempt + 2 retries = 3 total calls
        verify(processor, times(3)).process(state, context);
    }

    @Test
    void executeWithRetry_noRetryPolicy_returnsFailureImmediately() {
        StateProcessor processor = mock(StateProcessor.class);
        UUID stateId = UUID.randomUUID();
        StateDefinition state = new StateDefinition(
                stateId, StateType.API_CALL, "TestState", null, null,
                null, null); // No retry policy
        ExecutionContext context = new ExecutionContext(UUID.randomUUID(), null);

        when(processor.process(state, context))
                .thenReturn(StateProcessorResult.failure("Error"));

        StateProcessorResult result = retryManager.executeWithRetry(processor, state, context);

        assertEquals(StateOutcome.FAILED, result.getOutcome());
        verify(processor, times(1)).process(state, context);
        verify(retryAttemptRepository, never()).save(any());
    }

    @Test
    void executeWithRetry_maxRetriesZero_noRetries() {
        StateProcessor processor = mock(StateProcessor.class);
        UUID stateId = UUID.randomUUID();
        StateDefinition state = new StateDefinition(
                stateId, StateType.API_CALL, "TestState", null, null,
                new RetryPolicy(0, 5), null);
        ExecutionContext context = new ExecutionContext(UUID.randomUUID(), null);

        when(processor.process(state, context))
                .thenReturn(StateProcessorResult.failure("Error"));

        StateProcessorResult result = retryManager.executeWithRetry(processor, state, context);

        assertEquals(StateOutcome.FAILED, result.getOutcome());
        verify(processor, times(1)).process(state, context);
        verify(retryAttemptRepository, never()).save(any());
    }

    @Test
    void executeWithRetry_recordsCorrectAttemptNumbers() {
        StateProcessor processor = mock(StateProcessor.class);
        UUID stateId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        StateDefinition state = new StateDefinition(
                stateId, StateType.API_CALL, "TestState", null, null,
                new RetryPolicy(3, 1), null);
        ExecutionContext context = new ExecutionContext(executionId, null);

        // Always fail
        when(processor.process(state, context))
                .thenReturn(StateProcessorResult.failure("Error"));

        when(retryAttemptRepository.save(any(RetryAttemptEntity.class))).thenAnswer(invocation -> {
            RetryAttemptEntity entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });

        retryManager.executeWithRetry(processor, state, context);

        ArgumentCaptor<RetryAttemptEntity> captor = ArgumentCaptor.forClass(RetryAttemptEntity.class);
        verify(retryAttemptRepository, times(3)).save(captor.capture());

        // Verify attempt numbers are 1, 2, 3
        assertEquals(1, captor.getAllValues().get(0).getAttemptNumber());
        assertEquals(2, captor.getAllValues().get(1).getAttemptNumber());
        assertEquals(3, captor.getAllValues().get(2).getAttemptNumber());

        // All should have the same execution and state IDs
        for (RetryAttemptEntity entity : captor.getAllValues()) {
            assertEquals(executionId, entity.getExecutionId());
            assertEquals(stateId, entity.getStateId());
            assertEquals("Error", entity.getErrorMessage());
        }
    }

    @Test
    void executeWithRetry_sleepIsCalledWithCorrectDelays() {
        AtomicInteger sleepCallCount = new AtomicInteger(0);
        long[] capturedDelays = new long[3];
        Sleeper capturingSleeper = millis -> {
            int idx = sleepCallCount.getAndIncrement();
            if (idx < capturedDelays.length) {
                capturedDelays[idx] = millis;
            }
        };
        RetryManager managerWithCapture = new RetryManager(retryAttemptRepository, capturingSleeper);

        StateProcessor processor = mock(StateProcessor.class);
        UUID stateId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        StateDefinition state = new StateDefinition(
                stateId, StateType.API_CALL, "TestState", null, null,
                new RetryPolicy(3, 2), null); // base = 2 seconds
        ExecutionContext context = new ExecutionContext(executionId, null);

        when(processor.process(state, context))
                .thenReturn(StateProcessorResult.failure("Error"));
        when(retryAttemptRepository.save(any(RetryAttemptEntity.class))).thenAnswer(invocation -> {
            RetryAttemptEntity entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });

        managerWithCapture.executeWithRetry(processor, state, context);

        assertEquals(3, sleepCallCount.get());
        // attempt 1: 2 × 2^0 = 2s = 2000ms
        assertEquals(2000L, capturedDelays[0]);
        // attempt 2: 2 × 2^1 = 4s = 4000ms
        assertEquals(4000L, capturedDelays[1]);
        // attempt 3: 2 × 2^2 = 8s = 8000ms
        assertEquals(8000L, capturedDelays[2]);
    }
}

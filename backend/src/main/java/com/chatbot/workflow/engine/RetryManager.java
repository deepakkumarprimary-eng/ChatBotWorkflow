package com.chatbot.workflow.engine;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.chatbot.workflow.model.RetryPolicy;
import com.chatbot.workflow.model.StateDefinition;
import com.chatbot.workflow.model.StateOutcome;
import com.chatbot.workflow.repository.RetryAttemptEntity;
import com.chatbot.workflow.repository.RetryAttemptRepository;

/**
 * Manages retry logic with exponential backoff for state processing.
 * <p>
 * Formula: delay = baseInterval × 2^(attemptNumber - 1)
 * <p>
 * After retry exhaustion the caller (WorkflowEngine) resolves transitions
 * in priority order: error transition → fallback transition → halt execution.
 */
@Service
public class RetryManager {

    private static final Logger logger = LoggerFactory.getLogger(RetryManager.class);

    private final RetryAttemptRepository retryAttemptRepository;
    private final Sleeper sleeper;

    public RetryManager(RetryAttemptRepository retryAttemptRepository, Sleeper sleeper) {
        this.retryAttemptRepository = retryAttemptRepository;
        this.sleeper = sleeper;
    }

    /**
     * Validates that a RetryPolicy has acceptable parameter ranges.
     *
     * @param policy the retry policy to validate
     * @return true if maxRetries is in [0, 10] and backoffIntervalSeconds is in [1, 300]
     */
    public boolean validateRetryPolicy(RetryPolicy policy) {
        if (policy == null) {
            return false;
        }
        return policy.getMaxRetries() >= 0
                && policy.getMaxRetries() <= 10
                && policy.getBackoffIntervalSeconds() >= 1
                && policy.getBackoffIntervalSeconds() <= 300;
    }

    /**
     * Calculates the delay before a retry attempt using exponential backoff.
     *
     * @param baseIntervalSeconds the base interval in seconds from the retry policy
     * @param attemptNumber       the 1-based attempt number (1 = first retry)
     * @return delay in milliseconds: baseInterval × 2^(attemptNumber - 1) × 1000
     */
    public long calculateDelay(int baseIntervalSeconds, int attemptNumber) {
        // delay = baseInterval × 2^(attemptNumber - 1) seconds, returned as milliseconds
        long delaySeconds = (long) baseIntervalSeconds * (1L << (attemptNumber - 1));
        return delaySeconds * 1000L;
    }

    /**
     * Determines whether another retry attempt should be made.
     *
     * @param policy         the retry policy
     * @param currentAttempt the number of retry attempts already made (0-based count of retries so far)
     * @return true if currentAttempt < policy.maxRetries
     */
    public boolean shouldRetry(RetryPolicy policy, int currentAttempt) {
        if (policy == null) {
            return false;
        }
        return currentAttempt < policy.getMaxRetries();
    }

    /**
     * Records a retry attempt in the retry_attempts table.
     *
     * @param executionId   the execution ID
     * @param stateId       the state ID being retried
     * @param attemptNumber the 1-based attempt number
     * @param errorMessage  the error message that triggered the retry
     * @return the persisted entity
     */
    public RetryAttemptEntity recordAttempt(UUID executionId, UUID stateId, int attemptNumber, String errorMessage) {
        RetryAttemptEntity entity = new RetryAttemptEntity(executionId, stateId, attemptNumber, errorMessage);
        return retryAttemptRepository.save(entity);
    }

    /**
     * Executes a state processor with retry logic. On each failure:
     * <ol>
     *   <li>Checks if retry is allowed</li>
     *   <li>Records the retry attempt</li>
     *   <li>Waits using exponential backoff</li>
     *   <li>Retries the processor</li>
     * </ol>
     * If all retries are exhausted, returns the final failure result with
     * nextTransitionCondition = "error" so the WorkflowEngine can resolve
     * the appropriate transition (error → fallback → halt).
     *
     * @param processor the state processor to execute
     * @param state     the state definition
     * @param context   the execution context
     * @return the final StateProcessorResult (success if any attempt succeeds, failure after exhaustion)
     */
    public StateProcessorResult executeWithRetry(StateProcessor processor, StateDefinition state, ExecutionContext context) {
        RetryPolicy policy = state.getRetryPolicy();

        // Initial attempt (not counted as a retry)
        StateProcessorResult result = processor.process(state, context);

        if (result.getOutcome() != StateOutcome.FAILED) {
            return result;
        }

        // No retry policy or maxRetries == 0 means no retries
        if (policy == null || policy.getMaxRetries() <= 0) {
            return result;
        }

        // Validate the policy
        if (!validateRetryPolicy(policy)) {
            logger.warn("Invalid retry policy for state {}: {}", state.getName(), policy);
            return result;
        }

        UUID executionId = context.getExecutionId();
        UUID stateId = state.getId();

        // Retry loop
        int attempt = 0;
        while (shouldRetry(policy, attempt)) {
            attempt++;

            // Record the retry attempt
            String errorMsg = result.getErrorMessage() != null ? result.getErrorMessage() : "Unknown error";
            recordAttempt(executionId, stateId, attempt, errorMsg);

            // Wait with exponential backoff
            long delayMs = calculateDelay(policy.getBackoffIntervalSeconds(), attempt);
            logger.info("Retrying state '{}' (attempt {}/{}), waiting {}ms",
                    state.getName(), attempt, policy.getMaxRetries(), delayMs);

            try {
                sleeper.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Retry sleep interrupted for state '{}'", state.getName());
                return StateProcessorResult.failure("Retry interrupted: " + e.getMessage());
            }

            // Retry the processor
            result = processor.process(state, context);
            if (result.getOutcome() != StateOutcome.FAILED) {
                logger.info("State '{}' succeeded on retry attempt {}", state.getName(), attempt);
                return result;
            }
        }

        // All retries exhausted — return final failure with "error" transition condition
        logger.warn("All {} retries exhausted for state '{}'. Last error: {}",
                policy.getMaxRetries(), state.getName(), result.getErrorMessage());
        return StateProcessorResult.failure(result.getErrorMessage() != null
                ? result.getErrorMessage() : "All retries exhausted");
    }
}

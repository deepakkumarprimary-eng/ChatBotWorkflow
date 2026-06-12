package com.chatbot.workflow.engine;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.chatbot.workflow.model.StateDefinition;
import com.chatbot.workflow.model.StateOutcome;
import com.chatbot.workflow.model.StateType;

/**
 * Processor for Wait states. Validates the configured duration, stores wait metadata
 * in output variables, and signals a pause so the engine can save state.
 * The actual waiting is handled externally by a scheduler or polling mechanism.
 *
 * Config fields:
 * - "duration" (required): Wait duration in seconds (1-86400)
 *
 * Output variables set:
 * - "_waitDuration": The configured wait duration in seconds
 * - "_waitStartTime": ISO 8601 timestamp of when the wait began
 */
@Component
public class WaitStateProcessor implements StateProcessor {

    private static final Logger logger = LoggerFactory.getLogger(WaitStateProcessor.class);
    static final int MIN_DURATION_SECONDS = 1;
    static final int MAX_DURATION_SECONDS = 86400;

    @Override
    public StateType getType() {
        return StateType.WAIT;
    }

    @Override
    public StateProcessorResult process(StateDefinition state, ExecutionContext context) {
        Map<String, Object> config = state.getConfig();
        if (config == null) {
            return StateProcessorResult.failure("Wait state has no configuration");
        }

        // Validate required 'duration' field
        Object durationObj = config.get("duration");
        if (durationObj == null) {
            return StateProcessorResult.failure("Wait state is missing required 'duration' in configuration");
        }

        // Parse duration as integer
        int duration;
        try {
            duration = Integer.parseInt(durationObj.toString());
        } catch (NumberFormatException e) {
            return StateProcessorResult.failure(
                    "Wait state has non-numeric 'duration' value: " + durationObj);
        }

        // Validate range
        if (duration < MIN_DURATION_SECONDS || duration > MAX_DURATION_SECONDS) {
            return StateProcessorResult.failure(
                    "Wait state 'duration' must be between " + MIN_DURATION_SECONDS +
                    " and " + MAX_DURATION_SECONDS + " seconds, got: " + duration);
        }

        String waitStartTime = Instant.now().toString();

        logger.info("Wait state '{}' pausing execution for {}s. Start time: {}",
                state.getName(), duration, waitStartTime);

        // Store metadata in output variables for the scheduler/polling mechanism
        Map<String, Object> outputVariables = new HashMap<>();
        outputVariables.put("_waitDuration", duration);
        outputVariables.put("_waitStartTime", waitStartTime);

        // Signal pause — the WorkflowEngine saves state; external mechanism resumes after duration
        return StateProcessorResult.builder()
                .outcome(StateOutcome.SUCCEEDED)
                .outputVariables(outputVariables)
                .paused(true)
                .build();
    }
}

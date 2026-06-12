package com.chatbot.workflow.engine;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.chatbot.workflow.model.StateDefinition;
import com.chatbot.workflow.model.StateOutcome;
import com.chatbot.workflow.model.StateType;

/**
 * Processor for Input states. Validates configuration, stores prompt metadata
 * in output variables, and signals a pause so the engine can wait for user input.
 *
 * Config fields:
 * - "prompt" (required): The prompt message to display to the user
 * - "variableName" (required): The context variable name to store user input in
 * - "timeout" (optional): Timeout in seconds (default 300)
 *
 * Output variables set:
 * - "_inputPrompt": The prompt message for the frontend
 * - "_inputVariableName": The variable name where input will be stored
 * - "_inputTimeout": The configured timeout value in seconds
 */
@Component
public class InputStateProcessor implements StateProcessor {

    private static final Logger logger = LoggerFactory.getLogger(InputStateProcessor.class);
    private static final int DEFAULT_TIMEOUT_SECONDS = 300;

    @Override
    public StateType getType() {
        return StateType.INPUT;
    }

    @Override
    public StateProcessorResult process(StateDefinition state, ExecutionContext context) {
        Map<String, Object> config = state.getConfig();
        if (config == null) {
            return StateProcessorResult.failure("Input state has no configuration");
        }

        // Validate required fields
        Object promptObj = config.get("prompt");
        if (promptObj == null || promptObj.toString().isEmpty()) {
            return StateProcessorResult.failure("Input state is missing required 'prompt' in configuration");
        }

        Object variableNameObj = config.get("variableName");
        if (variableNameObj == null || variableNameObj.toString().isEmpty()) {
            return StateProcessorResult.failure("Input state is missing required 'variableName' in configuration");
        }

        String prompt = promptObj.toString();
        String variableName = variableNameObj.toString();

        // Extract timeout with default
        int timeout = DEFAULT_TIMEOUT_SECONDS;
        Object timeoutObj = config.get("timeout");
        if (timeoutObj != null) {
            try {
                timeout = Integer.parseInt(timeoutObj.toString());
            } catch (NumberFormatException e) {
                logger.warn("Invalid timeout value '{}' for Input state, using default {}s",
                        timeoutObj, DEFAULT_TIMEOUT_SECONDS);
            }
        }

        logger.info("Input state '{}' pausing execution. Prompt: '{}', variableName: '{}', timeout: {}s",
                state.getName(), prompt, variableName, timeout);

        // Store metadata in output variables so the engine/frontend knows what to display
        Map<String, Object> outputVariables = new HashMap<>();
        outputVariables.put("_inputPrompt", prompt);
        outputVariables.put("_inputVariableName", variableName);
        outputVariables.put("_inputTimeout", timeout);

        // Signal pause — the WorkflowEngine handles saving state and resuming later
        return StateProcessorResult.builder()
                .outcome(StateOutcome.SUCCEEDED)
                .outputVariables(outputVariables)
                .paused(true)
                .build();
    }
}

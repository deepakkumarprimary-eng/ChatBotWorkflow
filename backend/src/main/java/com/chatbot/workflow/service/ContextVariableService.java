package com.chatbot.workflow.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.chatbot.workflow.engine.ExecutionContext;
import com.chatbot.workflow.model.ContextVariable;

/**
 * Service for managing context variables within workflow executions.
 * Handles variable name validation, output mapping, and variable resolution
 * with proper undefined-variable handling.
 */
@Service
public class ContextVariableService {

    private static final Logger logger = LoggerFactory.getLogger(ContextVariableService.class);

    private static final Pattern VARIABLE_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{1,64}$");
    private static final int MAX_VARIABLES_PER_WORKFLOW = 100;

    /**
     * Validates a single variable name against the pattern ^[a-zA-Z0-9_]{1,64}$.
     *
     * @param name the variable name to validate
     * @return true if the name is valid, false otherwise
     */
    public boolean validateVariableName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        return VARIABLE_NAME_PATTERN.matcher(name).matches();
    }

    /**
     * Validates that the number of context variables does not exceed the maximum (100).
     *
     * @param variables the list of context variables to check
     * @return true if the count is within limits, false otherwise
     */
    public boolean validateVariableCount(List<ContextVariable> variables) {
        if (variables == null) {
            return true;
        }
        return variables.size() <= MAX_VARIABLES_PER_WORKFLOW;
    }

    /**
     * Validates all workflow context variables for both name format and count.
     * Returns a list of error messages (empty if all valid).
     *
     * @param variables the list of context variables to validate
     * @return list of error messages; empty if validation passes
     */
    public List<String> validateWorkflowVariables(List<ContextVariable> variables) {
        if (variables == null || variables.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> errors = new ArrayList<>();

        if (!validateVariableCount(variables)) {
            errors.add("Workflow exceeds maximum of " + MAX_VARIABLES_PER_WORKFLOW
                    + " context variables (has " + variables.size() + ")");
        }

        for (int i = 0; i < variables.size(); i++) {
            ContextVariable variable = variables.get(i);
            String name = variable.getName();
            if (!validateVariableName(name)) {
                errors.add("Invalid variable name at index " + i + ": '"
                        + (name == null ? "null" : name)
                        + "' (must match ^[a-zA-Z0-9_]{1,64}$)");
            }
        }

        return errors;
    }

    /**
     * Applies output mapping from a state's output to the execution context.
     * For each entry in outputMapping:
     *   - key = context variable name to write
     *   - value = key from stateOutput to extract the value from
     * If the mapped field doesn't exist in stateOutput, the context variable is set to null.
     *
     * @param context       the execution context to update
     * @param outputMapping mapping of context variable names to state output keys
     * @param stateOutput   the output produced by the state processor
     */
    public void applyOutputMapping(ExecutionContext context, Map<String, String> outputMapping,
                                   Map<String, Object> stateOutput) {
        if (context == null || outputMapping == null || outputMapping.isEmpty()) {
            return;
        }

        Map<String, Object> output = stateOutput != null ? stateOutput : Collections.emptyMap();

        for (Map.Entry<String, String> entry : outputMapping.entrySet()) {
            String contextVariableName = entry.getKey();
            String outputKey = entry.getValue();

            Object value = output.get(outputKey);
            context.setVariable(contextVariableName, value);
        }
    }

    /**
     * Resolves a context variable by name. If the variable does not exist in the context,
     * returns null and logs a warning including the variable name and state ID.
     *
     * @param context      the execution context to read from
     * @param variableName the name of the variable to resolve
     * @param stateId      the ID of the state requesting the variable (for logging)
     * @return the variable value, or null if undefined
     */
    public Object resolveVariable(ExecutionContext context, String variableName, UUID stateId) {
        if (context == null || variableName == null) {
            return null;
        }

        Map<String, Object> variables = context.getContextVariables();

        if (!variables.containsKey(variableName)) {
            logger.warn("Context variable '{}' referenced by state '{}' does not exist",
                    variableName, stateId);
            return null;
        }

        return variables.get(variableName);
    }
}

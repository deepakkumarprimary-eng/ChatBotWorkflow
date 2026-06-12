package com.chatbot.workflow.engine;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mutable context passed to state processors during execution.
 * Contains the execution ID and all context variables that states can read/write.
 */
public class ExecutionContext {

    private final UUID executionId;
    private final Map<String, Object> contextVariables;

    public ExecutionContext(UUID executionId, Map<String, Object> contextVariables) {
        this.executionId = executionId;
        this.contextVariables = contextVariables != null ? new HashMap<>(contextVariables) : new HashMap<>();
    }

    public UUID getExecutionId() {
        return executionId;
    }

    public Map<String, Object> getContextVariables() {
        return contextVariables;
    }

    /**
     * Get a context variable value by name. Returns null if the variable does not exist.
     */
    public Object getVariable(String name) {
        return contextVariables.get(name);
    }

    /**
     * Set a context variable value by name.
     */
    public void setVariable(String name, Object value) {
        contextVariables.put(name, value);
    }
}

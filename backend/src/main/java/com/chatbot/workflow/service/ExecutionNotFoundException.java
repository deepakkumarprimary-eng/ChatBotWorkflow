package com.chatbot.workflow.service;

import java.util.UUID;

/**
 * Exception thrown when an execution is not found.
 */
public class ExecutionNotFoundException extends RuntimeException {

    private final UUID executionId;

    public ExecutionNotFoundException(UUID executionId) {
        super("Execution not found: " + executionId);
        this.executionId = executionId;
    }

    public UUID getExecutionId() {
        return executionId;
    }
}

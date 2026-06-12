package com.chatbot.workflow.controller;

import java.util.UUID;

/**
 * Response DTO for POST /api/workflows/{id}/execute.
 */
public class ExecutionStartResponse {

    private UUID executionId;

    public ExecutionStartResponse() {
    }

    public ExecutionStartResponse(UUID executionId) {
        this.executionId = executionId;
    }

    public UUID getExecutionId() {
        return executionId;
    }

    public void setExecutionId(UUID executionId) {
        this.executionId = executionId;
    }
}

package com.chatbot.workflow.controller;

import java.time.Instant;
import java.util.UUID;

/**
 * Summary DTO for execution list endpoint.
 */
public class ExecutionListItem {

    private UUID executionId;
    private UUID workflowId;
    private String status;
    private Instant startTime;
    private Instant endTime;

    public ExecutionListItem() {
    }

    public ExecutionListItem(UUID executionId, UUID workflowId, String status,
                             Instant startTime, Instant endTime) {
        this.executionId = executionId;
        this.workflowId = workflowId;
        this.status = status;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public UUID getExecutionId() {
        return executionId;
    }

    public void setExecutionId(UUID executionId) {
        this.executionId = executionId;
    }

    public UUID getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(UUID workflowId) {
        this.workflowId = workflowId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }
}

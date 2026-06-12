package com.chatbot.workflow.controller;

import java.time.Instant;

/**
 * DTO for execution list items (paginated listing).
 */
public class ExecutionListItemDto {

    private String executionId;
    private String workflowId;
    private String status;
    private Instant startTime;
    private Instant endTime;

    public ExecutionListItemDto() {
    }

    public ExecutionListItemDto(String executionId, String workflowId, String status,
                                Instant startTime, Instant endTime) {
        this.executionId = executionId;
        this.workflowId = workflowId;
        this.status = status;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public String getExecutionId() {
        return executionId;
    }

    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
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

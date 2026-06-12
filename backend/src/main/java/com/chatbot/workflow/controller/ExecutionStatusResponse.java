package com.chatbot.workflow.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Full execution status response DTO including history.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExecutionStatusResponse {

    private UUID executionId;
    private UUID workflowId;
    private String status;
    private UUID currentStateId;
    private Instant startTime;
    private Instant endTime;
    private Long elapsedTimeMs;
    private String contextVariables;
    private String errorMessage;
    private String errorStackTrace;
    private List<ExecutionHistoryItem> history;

    public ExecutionStatusResponse() {
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

    public UUID getCurrentStateId() {
        return currentStateId;
    }

    public void setCurrentStateId(UUID currentStateId) {
        this.currentStateId = currentStateId;
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

    public Long getElapsedTimeMs() {
        return elapsedTimeMs;
    }

    public void setElapsedTimeMs(Long elapsedTimeMs) {
        this.elapsedTimeMs = elapsedTimeMs;
    }

    public String getContextVariables() {
        return contextVariables;
    }

    public void setContextVariables(String contextVariables) {
        this.contextVariables = contextVariables;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getErrorStackTrace() {
        return errorStackTrace;
    }

    public void setErrorStackTrace(String errorStackTrace) {
        this.errorStackTrace = errorStackTrace;
    }

    public List<ExecutionHistoryItem> getHistory() {
        return history;
    }

    public void setHistory(List<ExecutionHistoryItem> history) {
        this.history = history;
    }
}

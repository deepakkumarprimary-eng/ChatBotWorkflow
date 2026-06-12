package com.chatbot.workflow.controller;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO representing a single execution history entry.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExecutionHistoryEntryDto {

    private String stateId;
    private String stateName;
    private Instant entryTime;
    private Instant exitTime;
    private String outcome;
    private ErrorDetail error;

    public ExecutionHistoryEntryDto() {
    }

    public ExecutionHistoryEntryDto(String stateId, String stateName, Instant entryTime,
                                    Instant exitTime, String outcome, ErrorDetail error) {
        this.stateId = stateId;
        this.stateName = stateName;
        this.entryTime = entryTime;
        this.exitTime = exitTime;
        this.outcome = outcome;
        this.error = error;
    }

    public String getStateId() {
        return stateId;
    }

    public void setStateId(String stateId) {
        this.stateId = stateId;
    }

    public String getStateName() {
        return stateName;
    }

    public void setStateName(String stateName) {
        this.stateName = stateName;
    }

    public Instant getEntryTime() {
        return entryTime;
    }

    public void setEntryTime(Instant entryTime) {
        this.entryTime = entryTime;
    }

    public Instant getExitTime() {
        return exitTime;
    }

    public void setExitTime(Instant exitTime) {
        this.exitTime = exitTime;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public ErrorDetail getError() {
        return error;
    }

    public void setError(ErrorDetail error) {
        this.error = error;
    }

    /**
     * Nested error detail with message and truncated stack trace.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorDetail {
        private String message;
        private String stackTrace;

        public ErrorDetail() {
        }

        public ErrorDetail(String message, String stackTrace) {
            this.message = message;
            this.stackTrace = stackTrace;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getStackTrace() {
            return stackTrace;
        }

        public void setStackTrace(String stackTrace) {
            this.stackTrace = stackTrace;
        }
    }
}

package com.chatbot.workflow.controller;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for error details within an execution history item.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExecutionHistoryError {

    private String message;
    private String stackTrace;

    public ExecutionHistoryError() {
    }

    public ExecutionHistoryError(String message, String stackTrace) {
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

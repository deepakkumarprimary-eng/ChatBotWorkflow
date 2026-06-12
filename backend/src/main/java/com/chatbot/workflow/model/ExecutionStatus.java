package com.chatbot.workflow.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Represents the status of a workflow execution.
 */
public enum ExecutionStatus {
    RUNNING("running"),
    COMPLETED("completed"),
    FAILED("failed"),
    PAUSED("paused");

    private final String value;

    ExecutionStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ExecutionStatus fromValue(String value) {
        for (ExecutionStatus status : ExecutionStatus.values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown ExecutionStatus: " + value);
    }
}

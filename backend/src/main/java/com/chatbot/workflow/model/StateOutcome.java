package com.chatbot.workflow.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Represents the outcome of processing a single state during execution.
 */
public enum StateOutcome {
    SUCCEEDED("succeeded"),
    FAILED("failed"),
    SKIPPED("skipped"),
    TIMED_OUT("timed_out");

    private final String value;

    StateOutcome(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static StateOutcome fromValue(String value) {
        for (StateOutcome outcome : StateOutcome.values()) {
            if (outcome.value.equalsIgnoreCase(value)) {
                return outcome;
            }
        }
        throw new IllegalArgumentException("Unknown StateOutcome: " + value);
    }
}

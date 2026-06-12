package com.chatbot.workflow.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Defines the types of states available in a workflow.
 */
public enum StateType {
    API_CALL("api_call"),
    CONDITION("condition"),
    RESPONSE("response"),
    INPUT("input"),
    WAIT("wait"),
    PARALLEL("parallel"),
    END("end");

    private final String value;

    StateType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static StateType fromValue(String value) {
        for (StateType type : StateType.values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown StateType: " + value);
    }
}

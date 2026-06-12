package com.chatbot.workflow.controller;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO representing a single validation error in the REST response.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ValidationErrorDto {

    private String stateId;
    private String stateName;
    private String message;
    private String errorType;

    public ValidationErrorDto() {
    }

    public ValidationErrorDto(String stateId, String stateName, String message, String errorType) {
        this.stateId = stateId;
        this.stateName = stateName;
        this.message = message;
        this.errorType = errorType;
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

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getErrorType() {
        return errorType;
    }

    public void setErrorType(String errorType) {
        this.errorType = errorType;
    }
}

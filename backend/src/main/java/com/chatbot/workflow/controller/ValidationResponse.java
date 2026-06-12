package com.chatbot.workflow.controller;

import java.util.List;

/**
 * DTO representing the result of workflow validation.
 * Returns HTTP 200 regardless of whether the workflow is valid or not,
 * since validation errors are informational, not HTTP errors.
 */
public class ValidationResponse {

    private boolean valid;
    private List<ValidationErrorDto> errors;

    public ValidationResponse() {
    }

    public ValidationResponse(boolean valid, List<ValidationErrorDto> errors) {
        this.valid = valid;
        this.errors = errors;
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public List<ValidationErrorDto> getErrors() {
        return errors;
    }

    public void setErrors(List<ValidationErrorDto> errors) {
        this.errors = errors;
    }
}

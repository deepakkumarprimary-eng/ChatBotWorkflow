package com.chatbot.workflow.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Contains the result of workflow validation, including whether the workflow
 * is valid and all errors found during validation.
 */
public class ValidationResult {

    private final boolean valid;
    private final List<ValidationError> errors;

    private ValidationResult(boolean valid, List<ValidationError> errors) {
        this.valid = valid;
        this.errors = Collections.unmodifiableList(errors);
    }

    /**
     * Creates a successful validation result with no errors.
     */
    public static ValidationResult success() {
        return new ValidationResult(true, Collections.emptyList());
    }

    /**
     * Creates a failed validation result with the given errors.
     */
    public static ValidationResult failure(List<ValidationError> errors) {
        return new ValidationResult(false, new ArrayList<>(errors));
    }

    public boolean isValid() {
        return valid;
    }

    public List<ValidationError> getErrors() {
        return errors;
    }

    @Override
    public String toString() {
        return "ValidationResult{valid=" + valid + ", errors=" + errors.size() + "}";
    }
}

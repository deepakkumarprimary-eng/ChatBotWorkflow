package com.chatbot.workflow.service;

import java.util.UUID;

/**
 * Represents a single validation error found during workflow validation.
 * Contains the state ID (nullable for workflow-level errors), a human-readable message,
 * and an error type classification.
 */
public class ValidationError {

    private final UUID stateId;
    private final String stateName;
    private final String message;
    private final ValidationErrorType errorType;

    public ValidationError(UUID stateId, String stateName, String message, ValidationErrorType errorType) {
        this.stateId = stateId;
        this.stateName = stateName;
        this.message = message;
        this.errorType = errorType;
    }

    /**
     * Creates a workflow-level error (not associated with a specific state).
     */
    public static ValidationError workflowError(String message, ValidationErrorType errorType) {
        return new ValidationError(null, null, message, errorType);
    }

    /**
     * Creates a workflow-level error (not associated with a specific state).
     * Uses a default error type inferred from the message context.
     */
    public static ValidationError workflowError(String message) {
        ValidationErrorType type = inferWorkflowErrorType(message);
        return new ValidationError(null, null, message, type);
    }

    /**
     * Creates a state-level error.
     */
    public static ValidationError stateError(UUID stateId, String stateName, String message, ValidationErrorType errorType) {
        return new ValidationError(stateId, stateName, message, errorType);
    }

    /**
     * Creates a state-level error with stateId as String for backward compatibility.
     */
    public static ValidationError stateError(String stateId, String stateName, String message) {
        UUID uuid = stateId != null ? UUID.fromString(stateId) : null;
        ValidationErrorType type = inferStateErrorType(message);
        return new ValidationError(uuid, stateName, message, type);
    }

    public UUID getStateId() {
        return stateId;
    }

    public String getStateName() {
        return stateName;
    }

    public String getMessage() {
        return message;
    }

    public ValidationErrorType getErrorType() {
        return errorType;
    }

    private static ValidationErrorType inferWorkflowErrorType(String message) {
        if (message != null) {
            if (message.contains("empty")) {
                return ValidationErrorType.EMPTY_WORKFLOW;
            } else if (message.contains("No start state")) {
                return ValidationErrorType.NO_START_STATE;
            } else if (message.contains("Multiple start states")) {
                return ValidationErrorType.MULTIPLE_START_STATES;
            }
        }
        return ValidationErrorType.EMPTY_WORKFLOW;
    }

    private static ValidationErrorType inferStateErrorType(String message) {
        if (message != null) {
            if (message.contains("no outgoing")) {
                return ValidationErrorType.NO_OUTGOING_TRANSITION;
            } else if (message.contains("must have exactly 2") || message.contains("labeled 'true' and 'false'")) {
                return ValidationErrorType.CONDITION_TRANSITIONS;
            } else if (message.contains("not reachable") || message.contains("unreachable")) {
                return ValidationErrorType.UNREACHABLE_STATE;
            } else if (message.contains("missing required config")) {
                return ValidationErrorType.MISSING_CONFIG;
            } else if (message.contains("must be between") || message.contains("must be a") || message.contains("must be an")) {
                return ValidationErrorType.INVALID_CONFIG;
            }
        }
        return ValidationErrorType.MISSING_CONFIG;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ValidationError that = (ValidationError) o;
        if (stateId != null ? !stateId.equals(that.stateId) : that.stateId != null) return false;
        if (stateName != null ? !stateName.equals(that.stateName) : that.stateName != null) return false;
        if (errorType != that.errorType) return false;
        return message != null ? message.equals(that.message) : that.message == null;
    }

    @Override
    public int hashCode() {
        int result = stateId != null ? stateId.hashCode() : 0;
        result = 31 * result + (stateName != null ? stateName.hashCode() : 0);
        result = 31 * result + (message != null ? message.hashCode() : 0);
        result = 31 * result + (errorType != null ? errorType.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ValidationError{stateId=" + stateId + ", stateName='" + stateName +
                "', errorType=" + errorType + ", message='" + message + "'}";
    }
}

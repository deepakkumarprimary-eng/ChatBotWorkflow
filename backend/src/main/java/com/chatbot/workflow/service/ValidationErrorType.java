package com.chatbot.workflow.service;

/**
 * Categorizes the types of validation errors that can occur during workflow validation.
 */
public enum ValidationErrorType {
    EMPTY_WORKFLOW,
    NO_START_STATE,
    MULTIPLE_START_STATES,
    NO_OUTGOING_TRANSITION,
    CONDITION_TRANSITIONS,
    UNREACHABLE_STATE,
    MISSING_CONFIG,
    INVALID_CONFIG
}

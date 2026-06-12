package com.chatbot.workflow.service;

import java.util.UUID;

/**
 * Exception thrown when a workflow is not found or has been soft-deleted.
 */
public class WorkflowNotFoundException extends RuntimeException {

    private final UUID workflowId;

    public WorkflowNotFoundException(UUID workflowId) {
        super("Workflow not found: " + workflowId);
        this.workflowId = workflowId;
    }

    public UUID getWorkflowId() {
        return workflowId;
    }
}

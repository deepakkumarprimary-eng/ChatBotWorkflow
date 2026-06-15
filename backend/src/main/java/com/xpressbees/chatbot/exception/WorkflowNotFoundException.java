package com.xpressbees.chatbot.exception;

public class WorkflowNotFoundException extends RuntimeException {

    private final Long id;

    public WorkflowNotFoundException(Long id) {
        super("Workflow not found with id: " + id);
        this.id = id;
    }

    public Long getId() {
        return id;
    }
}

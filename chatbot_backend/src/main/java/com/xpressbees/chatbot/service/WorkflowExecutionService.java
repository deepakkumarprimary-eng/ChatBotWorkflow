package com.xpressbees.chatbot.service;

public interface WorkflowExecutionService {
    void startWorkflow(String sessionId, Long workflowId);
    void handleUserInput(String sessionId, String message);
}

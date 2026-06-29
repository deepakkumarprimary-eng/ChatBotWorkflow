package com.xpressbees.chatbot.dto;

import lombok.Data;
import java.util.Map;

@Data
public class NavigationResult {

    public enum Outcome { RESUME_NODE, UNAVAILABLE, ERROR }

    private final Outcome outcome;
    private final Map<String, Object> targetNode;
    private final Map<String, Object> workflowJson;
    private final String prompt;
    private final String errorMessage;

    private NavigationResult(Outcome outcome, Map<String, Object> targetNode,
                             Map<String, Object> workflowJson, String prompt, String errorMessage) {
        this.outcome = outcome;
        this.targetNode = targetNode;
        this.workflowJson = workflowJson;
        this.prompt = prompt;
        this.errorMessage = errorMessage;
    }

    public static NavigationResult resumeNode(Map<String, Object> targetNode,
                                               Map<String, Object> workflowJson,
                                               String prompt) {
        return new NavigationResult(Outcome.RESUME_NODE, targetNode, workflowJson, prompt, null);
    }

    public static NavigationResult unavailable() {
        return new NavigationResult(Outcome.UNAVAILABLE, null, null, null, null);
    }

    public static NavigationResult error(String message) {
        return new NavigationResult(Outcome.ERROR, null, null, null, message);
    }
}

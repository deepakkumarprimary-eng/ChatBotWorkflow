package com.xpressbees.chatbot.dto;

import lombok.Data;
import java.util.Map;

@Data
public class ChildWorkflowResult {

    public enum Outcome { NEXT_NODE, COMPLETE, ERROR }

    private final Outcome outcome;
    private final Map<String, Object> nextNode;
    private final Map<String, Object> workflowJson;
    private final String errorMessage;

    private ChildWorkflowResult(Outcome outcome, Map<String, Object> nextNode,
                                 Map<String, Object> workflowJson, String errorMessage) {
        this.outcome = outcome;
        this.nextNode = nextNode;
        this.workflowJson = workflowJson;
        this.errorMessage = errorMessage;
    }

    public static ChildWorkflowResult nextNode(Map<String, Object> node, Map<String, Object> workflowJson) {
        return new ChildWorkflowResult(Outcome.NEXT_NODE, node, workflowJson, null);
    }

    public static ChildWorkflowResult complete() {
        return new ChildWorkflowResult(Outcome.COMPLETE, null, null, null);
    }

    public static ChildWorkflowResult error(String message) {
        return new ChildWorkflowResult(Outcome.ERROR, null, null, message);
    }
}

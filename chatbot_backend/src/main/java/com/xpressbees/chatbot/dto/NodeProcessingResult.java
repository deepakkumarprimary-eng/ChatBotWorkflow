package com.xpressbees.chatbot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class NodeProcessingResult {

    private Action action;
    private ChatResponse response;
    private String errorMessage;  // populated when action == ERROR

    public enum Action {
        CONTINUE,
        PAUSE,
        COMPLETE,
        ENTER_CHILD,
        ERROR
    }

    // Backwards-compatible constructor
    public NodeProcessingResult(Action action, ChatResponse response) {
        this(action, response, null);
    }

    public static NodeProcessingResult error(String message) {
        return new NodeProcessingResult(Action.ERROR, null, message);
    }
}

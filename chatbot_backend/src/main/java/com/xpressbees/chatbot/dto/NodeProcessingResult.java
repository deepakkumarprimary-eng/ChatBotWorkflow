package com.xpressbees.chatbot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class NodeProcessingResult {

    private Action action;
    private ChatResponse response;

    public enum Action {
        CONTINUE,
        PAUSE,
        COMPLETE,
        ENTER_CHILD
    }
}

package com.xpressbees.chatbot.dto;

import com.xpressbees.chatbot.entity.ChatSession;
import lombok.Data;

@Data
public class SaveResult {
    private final boolean success;
    private final ChatSession session;
    private final String errorMessage;

    private SaveResult(boolean success, ChatSession session, String errorMessage) {
        this.success = success;
        this.session = session;
        this.errorMessage = errorMessage;
    }

    public static SaveResult success(ChatSession session) {
        return new SaveResult(true, session, null);
    }

    public static SaveResult failure(String errorMessage) {
        return new SaveResult(false, null, errorMessage);
    }
}

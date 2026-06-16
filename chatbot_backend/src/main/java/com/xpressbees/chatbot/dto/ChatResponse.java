package com.xpressbees.chatbot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatResponse {
    private Map<String, Object> node;
    private String response;
    private String sessionId;
    private Boolean completed;

    public ChatResponse(Map<String, Object> node, String response, String sessionId) {
        this.node = node;
        this.response = response;
        this.sessionId = sessionId;
    }
}

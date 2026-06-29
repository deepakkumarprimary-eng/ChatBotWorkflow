package com.xpressbees.chatbot.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatReconnectRequest {
    @NotBlank
    private String sessionId;
}

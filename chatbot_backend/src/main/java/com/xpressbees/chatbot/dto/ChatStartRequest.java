package com.xpressbees.chatbot.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatStartRequest {
    @NotBlank
    private String sessionId;

    private Long workflowId;
}

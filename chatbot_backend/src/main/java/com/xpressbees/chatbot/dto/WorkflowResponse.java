package com.xpressbees.chatbot.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class WorkflowResponse {
    private Long id;
    private String name;
    private Object workflowJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

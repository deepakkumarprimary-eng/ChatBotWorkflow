package com.xpressbees.chatbot.dto;

import lombok.Data;

@Data
public class WorkflowRequest {
    private String name;
    private Object workflowJson;
}

package com.xpressbees.chatbot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WorkflowRequest {
    @NotBlank
    @Size(max = 255)
    private String name;

    @NotNull
    private Object workflowJson;
}

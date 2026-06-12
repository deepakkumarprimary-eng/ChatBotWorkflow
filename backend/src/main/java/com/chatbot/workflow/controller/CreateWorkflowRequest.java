package com.chatbot.workflow.controller;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

import com.chatbot.workflow.model.WorkflowDefinition;

/**
 * Request DTO for creating a new workflow.
 */
public class CreateWorkflowRequest {

    @NotBlank(message = "Workflow name is required")
    @Size(min = 1, max = 100, message = "Workflow name must be between 1 and 100 characters")
    private String name;

    private String description;

    private WorkflowDefinition definition;

    public CreateWorkflowRequest() {
    }

    public CreateWorkflowRequest(String name, String description, WorkflowDefinition definition) {
        this.name = name;
        this.description = description;
        this.definition = definition;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public WorkflowDefinition getDefinition() {
        return definition;
    }

    public void setDefinition(WorkflowDefinition definition) {
        this.definition = definition;
    }
}

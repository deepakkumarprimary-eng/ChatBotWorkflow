package com.chatbot.workflow.controller;

import java.time.Instant;
import java.util.UUID;

import com.chatbot.workflow.model.WorkflowDefinition;

/**
 * Response DTO for a single workflow including its definition.
 */
public class WorkflowResponse {

    private UUID id;
    private String name;
    private String description;
    private int version;
    private Instant createdAt;
    private Instant lastModifiedAt;
    private WorkflowDefinition definition;

    public WorkflowResponse() {
    }

    public WorkflowResponse(UUID id, String name, String description, int version,
                            Instant createdAt, Instant lastModifiedAt, WorkflowDefinition definition) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.version = version;
        this.createdAt = createdAt;
        this.lastModifiedAt = lastModifiedAt;
        this.definition = definition;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastModifiedAt() {
        return lastModifiedAt;
    }

    public void setLastModifiedAt(Instant lastModifiedAt) {
        this.lastModifiedAt = lastModifiedAt;
    }

    public WorkflowDefinition getDefinition() {
        return definition;
    }

    public void setDefinition(WorkflowDefinition definition) {
        this.definition = definition;
    }
}

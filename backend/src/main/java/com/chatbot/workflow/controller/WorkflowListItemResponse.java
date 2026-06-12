package com.chatbot.workflow.controller;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for a workflow in a list (without the full definition).
 */
public class WorkflowListItemResponse {

    private UUID id;
    private String name;
    private String description;
    private int version;
    private Instant createdAt;
    private Instant lastModifiedAt;

    public WorkflowListItemResponse() {
    }

    public WorkflowListItemResponse(UUID id, String name, String description, int version,
                                    Instant createdAt, Instant lastModifiedAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.version = version;
        this.createdAt = createdAt;
        this.lastModifiedAt = lastModifiedAt;
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
}

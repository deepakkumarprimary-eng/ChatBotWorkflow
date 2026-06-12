package com.chatbot.workflow.repository;

import java.time.Instant;
import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

/**
 * JPA entity mapping to the 'workflow_versions' table.
 * Stores each version's workflow definition as a JSON string.
 */
@Entity
@Table(name = "workflow_versions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"workflow_id", "version"}))
public class WorkflowVersionEntity {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false, columnDefinition = "UUID")
    private UUID id;

    @Column(name = "workflow_id", nullable = false, columnDefinition = "UUID")
    private UUID workflowId;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "definition", nullable = false, columnDefinition = "jsonb")
    private String definition;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected WorkflowVersionEntity() {
        // Required by JPA
    }

    public WorkflowVersionEntity(UUID workflowId, int version, String definition) {
        this.workflowId = workflowId;
        this.version = version;
        this.definition = definition;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(UUID workflowId) {
        this.workflowId = workflowId;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getDefinition() {
        return definition;
    }

    public void setDefinition(String definition) {
        this.definition = definition;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

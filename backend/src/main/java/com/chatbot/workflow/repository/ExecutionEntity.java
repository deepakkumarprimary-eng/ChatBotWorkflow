package com.chatbot.workflow.repository;

import java.time.Instant;
import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.Table;

/**
 * JPA entity mapping to the 'executions' table.
 */
@Entity
@Table(name = "executions")
public class ExecutionEntity {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false, columnDefinition = "UUID")
    private UUID id;

    @Column(name = "workflow_id", nullable = false, columnDefinition = "UUID")
    private UUID workflowId;

    @Column(name = "workflow_version", nullable = false)
    private int workflowVersion;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "current_state_id", columnDefinition = "UUID")
    private UUID currentStateId;

    @Column(name = "context_variables", nullable = false, columnDefinition = "jsonb")
    private String contextVariables;

    @Column(name = "start_time", nullable = false, updatable = false)
    private Instant startTime;

    @Column(name = "end_time")
    private Instant endTime;

    @Column(name = "max_duration_seconds", nullable = false)
    private int maxDurationSeconds;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "error_stack_trace", length = 5000)
    private String errorStackTrace;

    protected ExecutionEntity() {
        // Required by JPA
    }

    public ExecutionEntity(UUID workflowId, int workflowVersion, String status, int maxDurationSeconds) {
        this.workflowId = workflowId;
        this.workflowVersion = workflowVersion;
        this.status = status;
        this.maxDurationSeconds = maxDurationSeconds;
        this.contextVariables = "{}";
    }

    @PrePersist
    protected void onCreate() {
        if (this.startTime == null) {
            this.startTime = Instant.now();
        }
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

    public int getWorkflowVersion() {
        return workflowVersion;
    }

    public void setWorkflowVersion(int workflowVersion) {
        this.workflowVersion = workflowVersion;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public UUID getCurrentStateId() {
        return currentStateId;
    }

    public void setCurrentStateId(UUID currentStateId) {
        this.currentStateId = currentStateId;
    }

    public String getContextVariables() {
        return contextVariables;
    }

    public void setContextVariables(String contextVariables) {
        this.contextVariables = contextVariables;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public int getMaxDurationSeconds() {
        return maxDurationSeconds;
    }

    public void setMaxDurationSeconds(int maxDurationSeconds) {
        this.maxDurationSeconds = maxDurationSeconds;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getErrorStackTrace() {
        return errorStackTrace;
    }

    public void setErrorStackTrace(String errorStackTrace) {
        this.errorStackTrace = errorStackTrace;
    }
}

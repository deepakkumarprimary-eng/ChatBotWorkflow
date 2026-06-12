package com.chatbot.workflow.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a single runtime instance of a workflow execution, tracking
 * its current state, context variables, and timing information.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Execution {

    private final UUID id;
    private final UUID workflowId;
    private final int workflowVersion;
    private ExecutionStatus status;
    private UUID currentStateId;
    private Map<String, Object> contextVariables;
    private final Instant startTime;
    private Instant endTime;
    private final int maxDurationSeconds;

    @JsonCreator
    public Execution(
            @JsonProperty("id") UUID id,
            @JsonProperty("workflowId") UUID workflowId,
            @JsonProperty("workflowVersion") int workflowVersion,
            @JsonProperty("status") ExecutionStatus status,
            @JsonProperty("currentStateId") UUID currentStateId,
            @JsonProperty("contextVariables") Map<String, Object> contextVariables,
            @JsonProperty("startTime") Instant startTime,
            @JsonProperty("endTime") Instant endTime,
            @JsonProperty("maxDurationSeconds") int maxDurationSeconds) {
        this.id = id;
        this.workflowId = workflowId;
        this.workflowVersion = workflowVersion;
        this.status = status;
        this.currentStateId = currentStateId;
        this.contextVariables = contextVariables;
        this.startTime = startTime;
        this.endTime = endTime;
        this.maxDurationSeconds = maxDurationSeconds;
    }

    @JsonProperty("id")
    public UUID getId() {
        return id;
    }

    @JsonProperty("workflowId")
    public UUID getWorkflowId() {
        return workflowId;
    }

    @JsonProperty("workflowVersion")
    public int getWorkflowVersion() {
        return workflowVersion;
    }

    @JsonProperty("status")
    public ExecutionStatus getStatus() {
        return status;
    }

    public void setStatus(ExecutionStatus status) {
        this.status = status;
    }

    @JsonProperty("currentStateId")
    public UUID getCurrentStateId() {
        return currentStateId;
    }

    public void setCurrentStateId(UUID currentStateId) {
        this.currentStateId = currentStateId;
    }

    @JsonProperty("contextVariables")
    public Map<String, Object> getContextVariables() {
        return contextVariables;
    }

    public void setContextVariables(Map<String, Object> contextVariables) {
        this.contextVariables = contextVariables;
    }

    @JsonProperty("startTime")
    public Instant getStartTime() {
        return startTime;
    }

    @JsonProperty("endTime")
    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    @JsonProperty("maxDurationSeconds")
    public int getMaxDurationSeconds() {
        return maxDurationSeconds;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Execution execution = (Execution) o;
        return id != null ? id.equals(execution.id) : execution.id == null;
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "Execution{id=" + id + ", workflowId=" + workflowId +
                ", status=" + status + ", version=" + workflowVersion + "}";
    }
}

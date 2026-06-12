package com.chatbot.workflow.controller;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for a single history entry in the execution status response.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExecutionHistoryItem {

    private UUID stateId;
    private String stateName;
    private Instant entryTime;
    private Instant exitTime;
    private String outcome;
    private ExecutionHistoryError error;

    public ExecutionHistoryItem() {
    }

    public ExecutionHistoryItem(UUID stateId, String stateName, Instant entryTime,
                                Instant exitTime, String outcome, ExecutionHistoryError error) {
        this.stateId = stateId;
        this.stateName = stateName;
        this.entryTime = entryTime;
        this.exitTime = exitTime;
        this.outcome = outcome;
        this.error = error;
    }

    public UUID getStateId() {
        return stateId;
    }

    public void setStateId(UUID stateId) {
        this.stateId = stateId;
    }

    public String getStateName() {
        return stateName;
    }

    public void setStateName(String stateName) {
        this.stateName = stateName;
    }

    public Instant getEntryTime() {
        return entryTime;
    }

    public void setEntryTime(Instant entryTime) {
        this.entryTime = entryTime;
    }

    public Instant getExitTime() {
        return exitTime;
    }

    public void setExitTime(Instant exitTime) {
        this.exitTime = exitTime;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public ExecutionHistoryError getError() {
        return error;
    }

    public void setError(ExecutionHistoryError error) {
        this.error = error;
    }
}

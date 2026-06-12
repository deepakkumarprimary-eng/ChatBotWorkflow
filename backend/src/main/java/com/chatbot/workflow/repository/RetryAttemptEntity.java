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
 * JPA entity mapping to the 'retry_attempts' table.
 * Records each retry attempt for a state within an execution.
 */
@Entity
@Table(name = "retry_attempts")
public class RetryAttemptEntity {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false, columnDefinition = "UUID")
    private UUID id;

    @Column(name = "execution_id", nullable = false, columnDefinition = "UUID")
    private UUID executionId;

    @Column(name = "state_id", nullable = false, columnDefinition = "UUID")
    private UUID stateId;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    protected RetryAttemptEntity() {
        // Required by JPA
    }

    public RetryAttemptEntity(UUID executionId, UUID stateId, int attemptNumber, String errorMessage) {
        this.executionId = executionId;
        this.stateId = stateId;
        this.attemptNumber = attemptNumber;
        this.errorMessage = errorMessage;
    }

    @PrePersist
    protected void onCreate() {
        if (this.attemptedAt == null) {
            this.attemptedAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getExecutionId() {
        return executionId;
    }

    public void setExecutionId(UUID executionId) {
        this.executionId = executionId;
    }

    public UUID getStateId() {
        return stateId;
    }

    public void setStateId(UUID stateId) {
        this.stateId = stateId;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public void setAttemptNumber(int attemptNumber) {
        this.attemptNumber = attemptNumber;
    }

    public Instant getAttemptedAt() {
        return attemptedAt;
    }

    public void setAttemptedAt(Instant attemptedAt) {
        this.attemptedAt = attemptedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}

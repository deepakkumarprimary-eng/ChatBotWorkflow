package com.chatbot.workflow.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for retry attempt records.
 */
@Repository
public interface RetryAttemptRepository extends JpaRepository<RetryAttemptEntity, UUID> {

    /**
     * Find all retry attempts for a given execution and state, ordered by attempt number.
     */
    List<RetryAttemptEntity> findByExecutionIdAndStateIdOrderByAttemptNumberAsc(UUID executionId, UUID stateId);
}

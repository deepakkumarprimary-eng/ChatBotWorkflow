package com.chatbot.workflow.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for execution history entities.
 */
@Repository
public interface ExecutionHistoryRepository extends JpaRepository<ExecutionHistoryEntity, UUID> {

    /**
     * Find all history entries for a given execution, ordered by sequence number.
     */
    List<ExecutionHistoryEntity> findByExecutionIdOrderBySequenceNumberAsc(UUID executionId);
}

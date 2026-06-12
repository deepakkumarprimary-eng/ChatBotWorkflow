package com.chatbot.workflow.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for execution entities.
 */
@Repository
public interface ExecutionRepository extends JpaRepository<ExecutionEntity, UUID> {

    /**
     * Find all executions for a given workflow, ordered by start time descending.
     */
    List<ExecutionEntity> findByWorkflowIdOrderByStartTimeDesc(UUID workflowId);

    /**
     * Find all executions with a given status.
     */
    List<ExecutionEntity> findByStatus(String status);
}

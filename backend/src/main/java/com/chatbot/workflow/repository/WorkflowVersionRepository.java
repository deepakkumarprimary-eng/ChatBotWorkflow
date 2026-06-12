package com.chatbot.workflow.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for workflow version entities.
 */
@Repository
public interface WorkflowVersionRepository extends JpaRepository<WorkflowVersionEntity, UUID> {

    /**
     * Find the latest version for a given workflow (highest version number).
     */
    Optional<WorkflowVersionEntity> findFirstByWorkflowIdOrderByVersionDesc(UUID workflowId);

    /**
     * Find a specific version of a workflow.
     */
    Optional<WorkflowVersionEntity> findByWorkflowIdAndVersion(UUID workflowId, int version);
}

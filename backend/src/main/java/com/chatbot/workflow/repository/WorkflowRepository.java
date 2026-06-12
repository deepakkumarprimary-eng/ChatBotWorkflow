package com.chatbot.workflow.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for workflow entities with support for soft-delete filtering.
 */
@Repository
public interface WorkflowRepository extends JpaRepository<WorkflowEntity, UUID> {

    /**
     * Find a non-deleted workflow by its ID.
     */
    @Query("SELECT w FROM WorkflowEntity w WHERE w.id = :id AND w.deletedAt IS NULL")
    Optional<WorkflowEntity> findByIdAndNotDeleted(@Param("id") UUID id);

    /**
     * Find all non-deleted workflows with pagination, sorted by lastModifiedAt DESC.
     */
    @Query("SELECT w FROM WorkflowEntity w WHERE w.deletedAt IS NULL")
    Page<WorkflowEntity> findAllNotDeleted(Pageable pageable);
}

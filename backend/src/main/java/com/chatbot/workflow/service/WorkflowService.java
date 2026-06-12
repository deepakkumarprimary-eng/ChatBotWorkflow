package com.chatbot.workflow.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatbot.workflow.model.WorkflowDefinition;
import com.chatbot.workflow.repository.WorkflowEntity;
import com.chatbot.workflow.repository.WorkflowRepository;
import com.chatbot.workflow.repository.WorkflowVersionEntity;
import com.chatbot.workflow.repository.WorkflowVersionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Service layer for workflow CRUD operations with versioning support.
 */
@Service
@Transactional
public class WorkflowService {

    private static final int MAX_PAGE_SIZE = 50;

    private final WorkflowRepository workflowRepository;
    private final WorkflowVersionRepository workflowVersionRepository;
    private final ObjectMapper objectMapper;

    public WorkflowService(WorkflowRepository workflowRepository,
                           WorkflowVersionRepository workflowVersionRepository,
                           ObjectMapper objectMapper) {
        this.workflowRepository = workflowRepository;
        this.workflowVersionRepository = workflowVersionRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates a new workflow with its first version.
     *
     * @param name       Workflow name (1-100 characters)
     * @param description Workflow description
     * @param definition The workflow definition to store as version 1
     * @return The created workflow entity with its ID populated
     * @throws IllegalArgumentException if name is null, empty, or longer than 100 characters
     */
    public WorkflowEntity createWorkflow(String name, String description, WorkflowDefinition definition) {
        validateWorkflowName(name);
        WorkflowEntity workflow = new WorkflowEntity(name, description);
        workflow = workflowRepository.saveAndFlush(workflow);

        String definitionJson = serializeDefinition(definition);
        WorkflowVersionEntity version = new WorkflowVersionEntity(workflow.getId(), 1, definitionJson);
        workflowVersionRepository.saveAndFlush(version);

        return workflow;
    }

    /**
     * Gets a workflow by ID, returning it with its latest definition.
     * Throws WorkflowNotFoundException if the workflow does not exist or is deleted.
     *
     * @param id The workflow UUID
     * @return The workflow with its latest definition
     */
    @Transactional(readOnly = true)
    public WorkflowWithDefinition getWorkflow(UUID id) {
        WorkflowEntity workflow = workflowRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new WorkflowNotFoundException(id));

        WorkflowVersionEntity latestVersion = workflowVersionRepository
                .findFirstByWorkflowIdOrderByVersionDesc(id)
                .orElseThrow(() -> new WorkflowNotFoundException(id));

        WorkflowDefinition definition = deserializeDefinition(latestVersion.getDefinition());

        return new WorkflowWithDefinition(workflow, definition);
    }

    /**
     * Updates a workflow: updates name/description, increments version,
     * and stores the new definition.
     *
     * @param id         The workflow UUID
     * @param name       Updated name
     * @param description Updated description
     * @param definition The new workflow definition
     * @return The updated workflow entity
     */
    public WorkflowEntity updateWorkflow(UUID id, String name, String description, WorkflowDefinition definition) {
        WorkflowEntity workflow = workflowRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new WorkflowNotFoundException(id));

        workflow.setName(name);
        workflow.setDescription(description);

        int newVersion = workflow.getCurrentVersion() + 1;
        workflow.setCurrentVersion(newVersion);
        workflow = workflowRepository.saveAndFlush(workflow);

        String definitionJson = serializeDefinition(definition);
        WorkflowVersionEntity versionEntity = new WorkflowVersionEntity(workflow.getId(), newVersion, definitionJson);
        workflowVersionRepository.saveAndFlush(versionEntity);

        return workflow;
    }

    /**
     * Soft-deletes a workflow by setting its deleted_at timestamp.
     *
     * @param id The workflow UUID
     */
    public void deleteWorkflow(UUID id) {
        WorkflowEntity workflow = workflowRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new WorkflowNotFoundException(id));

        workflow.setDeletedAt(Instant.now());
        workflowRepository.saveAndFlush(workflow);
    }

    /**
     * Lists non-deleted workflows with pagination, sorted by last_modified_at DESC.
     * Page size is capped at MAX_PAGE_SIZE (50).
     *
     * @param page Zero-based page index
     * @param size Requested page size (capped at 50)
     * @return Page of workflow entities
     */
    @Transactional(readOnly = true)
    public Page<WorkflowEntity> listWorkflows(int page, int size) {
        int effectiveSize = Math.min(size, MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(page, effectiveSize, Sort.by(Sort.Direction.DESC, "lastModifiedAt"));
        return workflowRepository.findAllNotDeleted(pageable);
    }

    private String serializeDefinition(WorkflowDefinition definition) {
        try {
            return objectMapper.writeValueAsString(definition);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize workflow definition", e);
        }
    }

    private WorkflowDefinition deserializeDefinition(String json) {
        try {
            return objectMapper.readValue(json, WorkflowDefinition.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize workflow definition", e);
        }
    }

    /**
     * Validates that the workflow name is between 1 and 100 characters.
     *
     * @param name The workflow name to validate
     * @throws IllegalArgumentException if name is null, empty, or longer than 100 characters
     */
    private void validateWorkflowName(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Workflow name is required and must not be empty");
        }
        if (name.length() > 100) {
            throw new IllegalArgumentException("Workflow name must not exceed 100 characters");
        }
    }

    /**
     * DTO combining a workflow entity with its current definition.
     */
    public static class WorkflowWithDefinition {
        private final WorkflowEntity workflow;
        private final WorkflowDefinition definition;

        public WorkflowWithDefinition(WorkflowEntity workflow, WorkflowDefinition definition) {
            this.workflow = workflow;
            this.definition = definition;
        }

        public WorkflowEntity getWorkflow() {
            return workflow;
        }

        public WorkflowDefinition getDefinition() {
            return definition;
        }
    }
}

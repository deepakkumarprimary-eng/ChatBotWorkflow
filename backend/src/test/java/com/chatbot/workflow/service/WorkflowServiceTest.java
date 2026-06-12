package com.chatbot.workflow.service;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.chatbot.workflow.model.Position;
import com.chatbot.workflow.model.StateDefinition;
import com.chatbot.workflow.model.StateType;
import com.chatbot.workflow.model.WorkflowDefinition;
import com.chatbot.workflow.model.WorkflowMetadata;
import com.chatbot.workflow.repository.WorkflowEntity;
import com.chatbot.workflow.repository.WorkflowRepository;
import com.chatbot.workflow.repository.WorkflowVersionEntity;
import com.chatbot.workflow.repository.WorkflowVersionRepository;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WorkflowServiceTest {

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private WorkflowRepository workflowRepository;

    @Autowired
    private WorkflowVersionRepository workflowVersionRepository;

    private WorkflowDefinition sampleDefinition;

    @BeforeEach
    void setUp() {
        WorkflowMetadata metadata = new WorkflowMetadata(
                "Test Workflow", "A test workflow", 1, Instant.now(), Instant.now());
        StateDefinition state = new StateDefinition(
                UUID.randomUUID(), StateType.END, "End State",
                new Position(100, 200), Collections.emptyMap(), null, null);
        sampleDefinition = new WorkflowDefinition(
                metadata, Collections.singletonList(state), Collections.emptyList(), Collections.emptyList());
    }

    @Test
    void createWorkflow_shouldPersistWorkflowAndVersion() {
        WorkflowEntity result = workflowService.createWorkflow("My Workflow", "Description", sampleDefinition);

        assertNotNull(result.getId());
        assertEquals("My Workflow", result.getName());
        assertEquals("Description", result.getDescription());
        assertEquals(1, result.getCurrentVersion());
        assertNotNull(result.getCreatedAt());
        assertNotNull(result.getLastModifiedAt());
        assertNull(result.getDeletedAt());

        // Verify version was created
        Optional<WorkflowVersionEntity> version = workflowVersionRepository
                .findByWorkflowIdAndVersion(result.getId(), 1);
        assertTrue(version.isPresent());
        assertEquals(1, version.get().getVersion());
        assertNotNull(version.get().getDefinition());
    }

    @Test
    void getWorkflow_shouldReturnWorkflowWithDefinition() {
        WorkflowEntity created = workflowService.createWorkflow("My Workflow", "Description", sampleDefinition);

        WorkflowService.WorkflowWithDefinition result = workflowService.getWorkflow(created.getId());

        assertEquals(created.getId(), result.getWorkflow().getId());
        assertEquals("My Workflow", result.getWorkflow().getName());
        assertNotNull(result.getDefinition());
        assertEquals(1, result.getDefinition().getStates().size());
    }

    @Test
    void getWorkflow_shouldThrowWhenNotFound() {
        UUID nonExistentId = UUID.randomUUID();
        assertThrows(WorkflowNotFoundException.class, () -> workflowService.getWorkflow(nonExistentId));
    }

    @Test
    void getWorkflow_shouldThrowWhenDeleted() {
        WorkflowEntity created = workflowService.createWorkflow("My Workflow", "Description", sampleDefinition);
        workflowService.deleteWorkflow(created.getId());

        assertThrows(WorkflowNotFoundException.class, () -> workflowService.getWorkflow(created.getId()));
    }

    @Test
    void updateWorkflow_shouldIncrementVersion() {
        WorkflowEntity created = workflowService.createWorkflow("My Workflow", "Description", sampleDefinition);

        WorkflowEntity updated = workflowService.updateWorkflow(
                created.getId(), "Updated Name", "Updated Desc", sampleDefinition);

        assertEquals(2, updated.getCurrentVersion());
        assertEquals("Updated Name", updated.getName());
        assertEquals("Updated Desc", updated.getDescription());

        // Verify new version exists
        Optional<WorkflowVersionEntity> version2 = workflowVersionRepository
                .findByWorkflowIdAndVersion(created.getId(), 2);
        assertTrue(version2.isPresent());
    }

    @Test
    void updateWorkflow_multipleUpdates_shouldIncrementMonotonically() {
        WorkflowEntity created = workflowService.createWorkflow("Workflow", "Desc", sampleDefinition);

        workflowService.updateWorkflow(created.getId(), "V2", "Desc", sampleDefinition);
        WorkflowEntity v3 = workflowService.updateWorkflow(created.getId(), "V3", "Desc", sampleDefinition);

        assertEquals(3, v3.getCurrentVersion());

        // All versions should exist
        assertTrue(workflowVersionRepository.findByWorkflowIdAndVersion(created.getId(), 1).isPresent());
        assertTrue(workflowVersionRepository.findByWorkflowIdAndVersion(created.getId(), 2).isPresent());
        assertTrue(workflowVersionRepository.findByWorkflowIdAndVersion(created.getId(), 3).isPresent());
    }

    @Test
    void updateWorkflow_shouldThrowWhenNotFound() {
        UUID nonExistentId = UUID.randomUUID();
        assertThrows(WorkflowNotFoundException.class,
                () -> workflowService.updateWorkflow(nonExistentId, "Name", "Desc", sampleDefinition));
    }

    @Test
    void deleteWorkflow_shouldSoftDelete() {
        WorkflowEntity created = workflowService.createWorkflow("My Workflow", "Description", sampleDefinition);
        workflowService.deleteWorkflow(created.getId());

        // Should not be findable via the non-deleted query
        Optional<WorkflowEntity> found = workflowRepository.findByIdAndNotDeleted(created.getId());
        assertFalse(found.isPresent());

        // But should still exist in the database
        Optional<WorkflowEntity> raw = workflowRepository.findById(created.getId());
        assertTrue(raw.isPresent());
        assertNotNull(raw.get().getDeletedAt());
    }

    @Test
    void deleteWorkflow_shouldThrowWhenNotFound() {
        UUID nonExistentId = UUID.randomUUID();
        assertThrows(WorkflowNotFoundException.class, () -> workflowService.deleteWorkflow(nonExistentId));
    }

    @Test
    void listWorkflows_shouldReturnPagedResults() {
        // Create 5 workflows
        for (int i = 0; i < 5; i++) {
            workflowService.createWorkflow("Workflow " + i, "Desc " + i, sampleDefinition);
        }

        Page<WorkflowEntity> page = workflowService.listWorkflows(0, 3);

        assertEquals(3, page.getContent().size());
        assertEquals(5, page.getTotalElements());
        assertEquals(2, page.getTotalPages());
    }

    @Test
    void listWorkflows_shouldNotIncludeDeletedWorkflows() {
        WorkflowEntity w1 = workflowService.createWorkflow("Workflow 1", "Desc", sampleDefinition);
        workflowService.createWorkflow("Workflow 2", "Desc", sampleDefinition);
        workflowService.deleteWorkflow(w1.getId());

        Page<WorkflowEntity> page = workflowService.listWorkflows(0, 50);

        assertEquals(1, page.getTotalElements());
        assertEquals("Workflow 2", page.getContent().get(0).getName());
    }

    @Test
    void listWorkflows_shouldCapPageSizeAt50() {
        // Create 3 workflows - just verify it doesn't fail with size > 50
        for (int i = 0; i < 3; i++) {
            workflowService.createWorkflow("Workflow " + i, "Desc", sampleDefinition);
        }

        Page<WorkflowEntity> page = workflowService.listWorkflows(0, 100);

        // Should still return all 3 (since total is less than 50)
        assertEquals(3, page.getContent().size());
        // Page size should be capped at 50
        assertEquals(50, page.getPageable().getPageSize());
    }

    @Test
    void listWorkflows_shouldBeSortedByLastModifiedDesc() {
        WorkflowEntity w1 = workflowService.createWorkflow("First", "Desc", sampleDefinition);
        WorkflowEntity w2 = workflowService.createWorkflow("Second", "Desc", sampleDefinition);
        // Update w1 to make it more recently modified
        workflowService.updateWorkflow(w1.getId(), "First Updated", "Desc", sampleDefinition);

        Page<WorkflowEntity> page = workflowService.listWorkflows(0, 50);

        assertEquals(2, page.getContent().size());
        assertEquals("First Updated", page.getContent().get(0).getName());
        assertEquals("Second", page.getContent().get(1).getName());
    }
}

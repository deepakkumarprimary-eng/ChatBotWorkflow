package com.chatbot.workflow.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.chatbot.workflow.model.Position;
import com.chatbot.workflow.model.StateDefinition;
import com.chatbot.workflow.model.StateType;
import com.chatbot.workflow.model.WorkflowDefinition;
import com.chatbot.workflow.model.WorkflowMetadata;
import com.chatbot.workflow.repository.WorkflowEntity;
import com.chatbot.workflow.repository.WorkflowRepository;
import com.chatbot.workflow.repository.WorkflowVersionEntity;
import com.chatbot.workflow.repository.WorkflowVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Tag;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;

/**
 * Property-based tests for workflow persistence operations.
 * Uses Mockito mocks for fast, isolated property testing.
 *
 * Validates: Requirements 3.1, 3.3, 3.5
 */
class WorkflowPersistenceProperties {

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    private static WorkflowDefinition createSampleDefinition() {
        WorkflowMetadata metadata = new WorkflowMetadata(
                "Test", "A test workflow", 1, Instant.now(), Instant.now());
        StateDefinition state = new StateDefinition(
                UUID.randomUUID(), StateType.END, "End State",
                new Position(0, 0), Collections.emptyMap(), null, null);
        return new WorkflowDefinition(
                metadata, Collections.singletonList(state), Collections.emptyList(), Collections.emptyList());
    }

    /**
     * Helper that creates a fresh service with fresh mocks for each property invocation.
     * Returns a holder containing the service and its mocked dependencies.
     */
    private static ServiceHolder createServiceWithMocks() {
        WorkflowRepository workflowRepo = mock(WorkflowRepository.class);
        WorkflowVersionRepository versionRepo = mock(WorkflowVersionRepository.class);
        ObjectMapper objectMapper = createObjectMapper();

        when(workflowRepo.saveAndFlush(any(WorkflowEntity.class))).thenAnswer(invocation -> {
            WorkflowEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(UUID.randomUUID());
            }
            if (entity.getCreatedAt() == null) {
                entity.setCreatedAt(Instant.now());
            }
            if (entity.getLastModifiedAt() == null) {
                entity.setLastModifiedAt(Instant.now());
            }
            return entity;
        });
        when(versionRepo.saveAndFlush(any(WorkflowVersionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WorkflowService service = new WorkflowService(workflowRepo, versionRepo, objectMapper);
        return new ServiceHolder(service, workflowRepo, versionRepo);
    }

    private static class ServiceHolder {
        final WorkflowService service;
        final WorkflowRepository workflowRepo;
        final WorkflowVersionRepository versionRepo;

        ServiceHolder(WorkflowService service, WorkflowRepository workflowRepo,
                      WorkflowVersionRepository versionRepo) {
            this.service = service;
            this.workflowRepo = workflowRepo;
            this.versionRepo = versionRepo;
        }
    }

    // ========================================================================
    // Property 9: Workflow name length validation
    // For any string, it should be accepted as a workflow name if and only if
    // its length is between 1 and 100 characters (inclusive).
    // ========================================================================

    /**
     * Property 9: Valid names (1-100 chars) are accepted.
     *
     * **Validates: Requirements 3.1**
     */
    @Property(tries = 100)
    @Tag("Feature: chatbot-workflow-builder, Property 9: Workflow name length validation")
    void validNamesShouldBeAccepted(@ForAll @StringLength(min = 1, max = 100) String name) {
        ServiceHolder holder = createServiceWithMocks();
        WorkflowDefinition definition = createSampleDefinition();

        WorkflowEntity result = holder.service.createWorkflow(name, "desc", definition);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo(name);
    }

    /**
     * Property 9: Empty names (length 0) are rejected.
     *
     * **Validates: Requirements 3.1**
     */
    @Property(tries = 100)
    @Tag("Feature: chatbot-workflow-builder, Property 9: Workflow name length validation")
    void emptyNameShouldBeRejected() {
        ServiceHolder holder = createServiceWithMocks();
        WorkflowDefinition definition = createSampleDefinition();

        assertThatThrownBy(() -> holder.service.createWorkflow("", "desc", definition))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Property 9: Null name is rejected.
     *
     * **Validates: Requirements 3.1**
     */
    @Property(tries = 100)
    @Tag("Feature: chatbot-workflow-builder, Property 9: Workflow name length validation")
    void nullNameShouldBeRejected() {
        ServiceHolder holder = createServiceWithMocks();
        WorkflowDefinition definition = createSampleDefinition();

        assertThatThrownBy(() -> holder.service.createWorkflow(null, "desc", definition))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Property 9: Names longer than 100 characters are rejected.
     *
     * **Validates: Requirements 3.1**
     */
    @Property(tries = 100)
    @Tag("Feature: chatbot-workflow-builder, Property 9: Workflow name length validation")
    void tooLongNameShouldBeRejected(@ForAll @StringLength(min = 101, max = 300) String name) {
        ServiceHolder holder = createServiceWithMocks();
        WorkflowDefinition definition = createSampleDefinition();

        assertThatThrownBy(() -> holder.service.createWorkflow(name, "desc", definition))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ========================================================================
    // Property 10: Workflow listing pagination and sorting
    // For any set of workflows, paginated listing should return at most 50 items
    // per page, sorted by last modified date descending.
    // ========================================================================

    /**
     * Property 10: Page size is capped at 50 regardless of requested size.
     *
     * **Validates: Requirements 3.3**
     */
    @Property(tries = 100)
    @Tag("Feature: chatbot-workflow-builder, Property 10: Workflow listing pagination and sorting")
    void listingShouldCapPageSizeAt50(@ForAll @IntRange(min = 1, max = 200) int requestedSize) {
        ServiceHolder holder = createServiceWithMocks();

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(holder.workflowRepo.findAllNotDeleted(any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        holder.service.listWorkflows(0, requestedSize);

        verify(holder.workflowRepo).findAllNotDeleted(pageableCaptor.capture());
        Pageable capturedPageable = pageableCaptor.getValue();

        assertThat(capturedPageable.getPageSize()).isLessThanOrEqualTo(50);
    }

    /**
     * Property 10: Results are sorted by lastModifiedAt descending.
     *
     * **Validates: Requirements 3.3**
     */
    @Property(tries = 100)
    @Tag("Feature: chatbot-workflow-builder, Property 10: Workflow listing pagination and sorting")
    void listingShouldBeSortedByLastModifiedDescending(@ForAll @IntRange(min = 2, max = 50) int workflowCount) {
        ServiceHolder holder = createServiceWithMocks();

        // Create mock workflows with descending lastModifiedAt timestamps
        List<WorkflowEntity> workflows = new ArrayList<>();
        Instant baseTime = Instant.now();
        for (int i = 0; i < workflowCount; i++) {
            WorkflowEntity entity = new WorkflowEntity("Workflow " + i, "Desc");
            entity.setId(UUID.randomUUID());
            entity.setLastModifiedAt(baseTime.minusSeconds(i));
            entity.setCreatedAt(baseTime.minusSeconds(workflowCount + i));
            workflows.add(entity);
        }

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(holder.workflowRepo.findAllNotDeleted(any(Pageable.class)))
                .thenReturn(new PageImpl<>(workflows));

        Page<WorkflowEntity> page = holder.service.listWorkflows(0, 50);

        verify(holder.workflowRepo).findAllNotDeleted(pageableCaptor.capture());
        Pageable capturedPageable = pageableCaptor.getValue();

        // Verify the sort is DESC on lastModifiedAt
        assertThat(capturedPageable.getSort().getOrderFor("lastModifiedAt")).isNotNull();
        assertThat(capturedPageable.getSort().getOrderFor("lastModifiedAt").isDescending()).isTrue();

        // Verify returned content is in descending lastModifiedAt order
        List<WorkflowEntity> results = page.getContent();
        for (int i = 0; i < results.size() - 1; i++) {
            Instant current = results.get(i).getLastModifiedAt();
            Instant next = results.get(i + 1).getLastModifiedAt();
            assertThat(current).isAfterOrEqualTo(next);
        }
    }

    /**
     * Property 10: Page content size never exceeds 50 even when more is requested.
     *
     * **Validates: Requirements 3.3**
     */
    @Property(tries = 100)
    @Tag("Feature: chatbot-workflow-builder, Property 10: Workflow listing pagination and sorting")
    void listingContentShouldNeverExceed50Items(@ForAll @IntRange(min = 51, max = 200) int requestedSize) {
        ServiceHolder holder = createServiceWithMocks();

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(holder.workflowRepo.findAllNotDeleted(any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        holder.service.listWorkflows(0, requestedSize);

        verify(holder.workflowRepo).findAllNotDeleted(pageableCaptor.capture());
        Pageable capturedPageable = pageableCaptor.getValue();

        // The effective page size should be capped at 50
        assertThat(capturedPageable.getPageSize()).isEqualTo(50);
    }

    // ========================================================================
    // Property 11: Version number monotonic increment
    // For any workflow, a sequence of N saves should produce version numbers
    // [1, 2, 3, ..., N].
    // ========================================================================

    /**
     * Property 11: Initial save produces version 1.
     *
     * **Validates: Requirements 3.5**
     */
    @Property(tries = 100)
    @Tag("Feature: chatbot-workflow-builder, Property 11: Version number monotonic increment")
    void initialSaveShouldProduceVersion1(@ForAll @StringLength(min = 1, max = 100) String name) {
        ServiceHolder holder = createServiceWithMocks();
        WorkflowDefinition definition = createSampleDefinition();

        WorkflowEntity result = holder.service.createWorkflow(name, "desc", definition);

        assertThat(result.getCurrentVersion()).isEqualTo(1);
    }

    /**
     * Property 11: N saves produce version numbers [1, 2, ..., N].
     *
     * **Validates: Requirements 3.5**
     */
    @Property(tries = 100)
    @Tag("Feature: chatbot-workflow-builder, Property 11: Version number monotonic increment")
    void sequentialSavesShouldProduceMonotonicallyIncreasingVersions(
            @ForAll @IntRange(min = 2, max = 10) int totalSaves) {
        ServiceHolder holder = createServiceWithMocks();
        WorkflowDefinition definition = createSampleDefinition();

        // Create the workflow (version 1)
        WorkflowEntity workflow = holder.service.createWorkflow("TestWorkflow", "desc", definition);
        UUID workflowId = workflow.getId();
        assertThat(workflow.getCurrentVersion()).isEqualTo(1);

        // Perform sequential updates and verify version numbers
        WorkflowEntity current = workflow;
        for (int i = 2; i <= totalSaves; i++) {
            final WorkflowEntity stateBefore = current;
            when(holder.workflowRepo.findByIdAndNotDeleted(workflowId))
                    .thenReturn(Optional.of(stateBefore));

            current = holder.service.updateWorkflow(workflowId, "TestWorkflow v" + i, "desc", definition);
            assertThat(current.getCurrentVersion()).isEqualTo(i);
        }

        // Final version should be exactly totalSaves
        assertThat(current.getCurrentVersion()).isEqualTo(totalSaves);
    }

    /**
     * Property 11: Version numbers are strictly sequential with no gaps.
     *
     * **Validates: Requirements 3.5**
     */
    @Property(tries = 100)
    @Tag("Feature: chatbot-workflow-builder, Property 11: Version number monotonic increment")
    void versionNumbersShouldHaveNoGaps(@ForAll @IntRange(min = 2, max = 8) int totalSaves) {
        ServiceHolder holder = createServiceWithMocks();
        WorkflowDefinition definition = createSampleDefinition();

        // Create the workflow (version 1)
        WorkflowEntity workflow = holder.service.createWorkflow("GapTest", "desc", definition);
        UUID workflowId = workflow.getId();

        List<Integer> versions = new ArrayList<>();
        versions.add(workflow.getCurrentVersion());

        // Perform sequential updates
        WorkflowEntity current = workflow;
        for (int i = 2; i <= totalSaves; i++) {
            final WorkflowEntity stateBefore = current;
            when(holder.workflowRepo.findByIdAndNotDeleted(workflowId))
                    .thenReturn(Optional.of(stateBefore));

            current = holder.service.updateWorkflow(workflowId, "GapTest v" + i, "desc", definition);
            versions.add(current.getCurrentVersion());
        }

        // Verify the version sequence is exactly [1, 2, 3, ..., totalSaves]
        for (int i = 0; i < versions.size(); i++) {
            assertThat(versions.get(i)).isEqualTo(i + 1);
        }
    }
}

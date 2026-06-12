package com.chatbot.workflow.controller;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chatbot.workflow.engine.WorkflowEngine;
import com.chatbot.workflow.repository.ExecutionEntity;
import com.chatbot.workflow.repository.ExecutionHistoryEntity;
import com.chatbot.workflow.repository.ExecutionHistoryRepository;
import com.chatbot.workflow.repository.ExecutionRepository;
import com.chatbot.workflow.service.ExecutionNotFoundException;
import com.chatbot.workflow.service.WorkflowService;
import com.chatbot.workflow.service.WorkflowService.WorkflowWithDefinition;

/**
 * REST controller for workflow execution and execution monitoring endpoints.
 */
@RestController
@RequestMapping("/api")
public class ExecutionController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_STACK_TRACE_LENGTH = 5000;

    private final WorkflowService workflowService;
    private final WorkflowEngine workflowEngine;
    private final ExecutionRepository executionRepository;
    private final ExecutionHistoryRepository executionHistoryRepository;

    public ExecutionController(WorkflowService workflowService,
                               WorkflowEngine workflowEngine,
                               ExecutionRepository executionRepository,
                               ExecutionHistoryRepository executionHistoryRepository) {
        this.workflowService = workflowService;
        this.workflowEngine = workflowEngine;
        this.executionRepository = executionRepository;
        this.executionHistoryRepository = executionHistoryRepository;
    }

    /**
     * Start a workflow execution.
     * Returns 202 Accepted with the execution ID.
     */
    @PostMapping("/workflows/{id}/execute")
    public ResponseEntity<ExecutionStartResponse> executeWorkflow(@PathVariable UUID id) {
        WorkflowWithDefinition workflowWithDef = workflowService.getWorkflow(id);

        UUID executionId = workflowEngine.startExecution(
                workflowWithDef.getWorkflow().getId(),
                workflowWithDef.getWorkflow().getCurrentVersion(),
                workflowWithDef.getDefinition()
        );

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new ExecutionStartResponse(executionId));
    }

    /**
     * List executions with pagination. Default page size is 20, max is 100.
     * Sorted by start_time DESC.
     */
    @GetMapping("/executions")
    public ResponseEntity<Page<ExecutionListItem>> listExecutions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int effectiveSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(page, effectiveSize, Sort.by(Sort.Direction.DESC, "startTime"));

        Page<ExecutionEntity> executionPage = executionRepository.findAll(pageable);

        Page<ExecutionListItem> responsePage = executionPage.map(entity ->
                new ExecutionListItem(
                        entity.getId(),
                        entity.getWorkflowId(),
                        entity.getStatus(),
                        entity.getStartTime(),
                        entity.getEndTime()
                )
        );

        return ResponseEntity.ok(responsePage);
    }

    /**
     * Get execution status with full details including history.
     * Returns 404 if execution not found.
     */
    @GetMapping("/executions/{id}")
    public ResponseEntity<ExecutionStatusResponse> getExecution(@PathVariable UUID id) {
        ExecutionEntity execution = executionRepository.findById(id)
                .orElseThrow(() -> new ExecutionNotFoundException(id));

        List<ExecutionHistoryEntity> historyEntities =
                executionHistoryRepository.findByExecutionIdOrderBySequenceNumberAsc(id);

        List<ExecutionHistoryItem> historyItems = historyEntities.stream()
                .map(this::toHistoryItem)
                .collect(Collectors.toList());

        ExecutionStatusResponse response = new ExecutionStatusResponse();
        response.setExecutionId(execution.getId());
        response.setWorkflowId(execution.getWorkflowId());
        response.setStatus(execution.getStatus());
        response.setCurrentStateId(execution.getCurrentStateId());
        response.setStartTime(execution.getStartTime());
        response.setEndTime(execution.getEndTime());
        response.setElapsedTimeMs(calculateElapsedTimeMs(execution));
        response.setContextVariables(execution.getContextVariables());
        response.setErrorMessage(execution.getErrorMessage());
        response.setErrorStackTrace(truncateStackTrace(execution.getErrorStackTrace()));
        response.setHistory(historyItems);

        return ResponseEntity.ok(response);
    }

    private ExecutionHistoryItem toHistoryItem(ExecutionHistoryEntity entity) {
        ExecutionHistoryError error = null;
        if (entity.getErrorMessage() != null || entity.getErrorStackTrace() != null) {
            error = new ExecutionHistoryError(
                    entity.getErrorMessage(),
                    truncateStackTrace(entity.getErrorStackTrace())
            );
        }

        return new ExecutionHistoryItem(
                entity.getStateId(),
                entity.getStateName(),
                entity.getEntryTime(),
                entity.getExitTime(),
                entity.getOutcome(),
                error
        );
    }

    private Long calculateElapsedTimeMs(ExecutionEntity execution) {
        if (execution.getStartTime() == null) {
            return null;
        }
        Instant end = execution.getEndTime() != null ? execution.getEndTime() : Instant.now();
        return Duration.between(execution.getStartTime(), end).toMillis();
    }

    /**
     * Truncate stack trace to MAX_STACK_TRACE_LENGTH characters.
     */
    static String truncateStackTrace(String stackTrace) {
        if (stackTrace == null) {
            return null;
        }
        if (stackTrace.length() <= MAX_STACK_TRACE_LENGTH) {
            return stackTrace;
        }
        return stackTrace.substring(0, MAX_STACK_TRACE_LENGTH);
    }
}

package com.chatbot.workflow.engine;

import com.chatbot.workflow.model.ContextVariable;
import com.chatbot.workflow.model.ExecutionStatus;
import com.chatbot.workflow.model.StateDefinition;
import com.chatbot.workflow.model.StateOutcome;
import com.chatbot.workflow.model.StateType;
import com.chatbot.workflow.model.TransitionDefinition;
import com.chatbot.workflow.model.WorkflowDefinition;
import com.chatbot.workflow.repository.ExecutionEntity;
import com.chatbot.workflow.repository.ExecutionHistoryEntity;
import com.chatbot.workflow.repository.ExecutionHistoryRepository;
import com.chatbot.workflow.repository.ExecutionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Core workflow execution engine. Orchestrates state processing, context variable management,
 * transition resolution, and execution lifecycle (start, process, complete/fail/pause).
 */
@Service
public class WorkflowEngine {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowEngine.class);
    private static final int DEFAULT_MAX_DURATION_SECONDS = 3600;

    private final Map<StateType, StateProcessor> processorMap;
    private final ExecutionRepository executionRepository;
    private final ExecutionHistoryRepository historyRepository;
    private final ObjectMapper objectMapper;

    public WorkflowEngine(List<StateProcessor> processors,
                          ExecutionRepository executionRepository,
                          ExecutionHistoryRepository historyRepository,
                          ObjectMapper objectMapper) {
        this.processorMap = processors.stream()
                .collect(Collectors.toMap(StateProcessor::getType, p -> p));
        this.executionRepository = executionRepository;
        this.historyRepository = historyRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Start a new execution for the given workflow definition.
     * Initializes context variables from defaults, creates the execution record,
     * and begins the processing loop.
     *
     * @param workflowId  the workflow ID
     * @param version     the workflow version
     * @param definition  the full workflow definition
     * @return the execution ID
     */
    @Transactional
    public UUID startExecution(UUID workflowId, int version, WorkflowDefinition definition) {
        // 1. Find the start state (no incoming transitions)
        StateDefinition startState = findStartState(definition);
        if (startState == null) {
            throw new IllegalArgumentException("Workflow has no valid start state");
        }

        // 2. Initialize context variables from workflow defaults
        Map<String, Object> contextVariables = initializeContextVariables(definition.getContextVariables());

        // 3. Create execution entity
        ExecutionEntity execution = new ExecutionEntity(workflowId, version,
                ExecutionStatus.RUNNING.getValue(), DEFAULT_MAX_DURATION_SECONDS);
        execution.setCurrentStateId(startState.getId());
        execution.setContextVariables(serializeContext(contextVariables));
        execution.setStartTime(Instant.now());
        execution = executionRepository.save(execution);

        UUID executionId = execution.getId();
        logger.info("Started execution {} for workflow {} version {}", executionId, workflowId, version);

        // 4. Begin processing loop
        ExecutionContext context = new ExecutionContext(executionId, contextVariables);
        processStates(execution, startState, context, definition);

        return executionId;
    }

    /**
     * Resume a paused execution (e.g., after Input state receives user input).
     *
     * @param executionId the execution ID to resume
     * @param input       user-provided input variables to merge into context
     */
    @Transactional
    public void resumeExecution(UUID executionId, Map<String, Object> input) {
        Optional<ExecutionEntity> optExecution = executionRepository.findById(executionId);
        if (!optExecution.isPresent()) {
            throw new IllegalArgumentException("Execution not found: " + executionId);
        }

        ExecutionEntity execution = optExecution.get();
        if (!ExecutionStatus.PAUSED.getValue().equals(execution.getStatus())) {
            throw new IllegalStateException("Execution is not paused: " + executionId);
        }

        // Deserialize context and merge input
        Map<String, Object> contextVariables = deserializeContext(execution.getContextVariables());
        if (input != null) {
            contextVariables.putAll(input);
        }

        execution.setStatus(ExecutionStatus.RUNNING.getValue());
        execution.setContextVariables(serializeContext(contextVariables));
        execution = executionRepository.save(execution);

        // We need the full workflow definition to continue processing.
        // For resume, we need a workflow definition. This is stored in workflow_versions.
        // For now, the caller must provide it or we find next state from execution's current state.
        // This will be enhanced when the execution endpoint is implemented.
        logger.info("Resumed execution {}", executionId);
    }

    /**
     * Resume a paused execution with the full workflow definition (used internally).
     */
    @Transactional
    public void resumeExecution(UUID executionId, Map<String, Object> input, WorkflowDefinition definition) {
        Optional<ExecutionEntity> optExecution = executionRepository.findById(executionId);
        if (!optExecution.isPresent()) {
            throw new IllegalArgumentException("Execution not found: " + executionId);
        }

        ExecutionEntity execution = optExecution.get();
        if (!ExecutionStatus.PAUSED.getValue().equals(execution.getStatus())) {
            throw new IllegalStateException("Execution is not paused: " + executionId);
        }

        // Deserialize context and merge input
        Map<String, Object> contextVariables = deserializeContext(execution.getContextVariables());
        if (input != null) {
            contextVariables.putAll(input);
        }

        execution.setStatus(ExecutionStatus.RUNNING.getValue());
        execution.setContextVariables(serializeContext(contextVariables));
        execution = executionRepository.save(execution);

        // Find current state and determine next state
        UUID currentStateId = execution.getCurrentStateId();
        StateDefinition currentState = findStateById(definition, currentStateId);
        if (currentState == null) {
            markFailed(execution, "Current state not found: " + currentStateId);
            return;
        }

        // Find the next state after the paused state (follow default transition)
        UUID nextStateId = resolveNextState(currentState.getId(), null, definition.getTransitions());
        if (nextStateId == null) {
            // No outgoing transition from paused state — complete
            markCompleted(execution, contextVariables);
            return;
        }

        StateDefinition nextState = findStateById(definition, nextStateId);
        if (nextState == null) {
            markFailed(execution, "Next state not found: " + nextStateId);
            return;
        }

        execution.setCurrentStateId(nextStateId);
        execution = executionRepository.save(execution);

        ExecutionContext context = new ExecutionContext(executionId, contextVariables);
        processStates(execution, nextState, context, definition);

        logger.info("Resumed execution {} to state {}", executionId, nextState.getName());
    }

    /**
     * The main processing loop. Processes states sequentially until completion, failure, pause, or timeout.
     */
    private void processStates(ExecutionEntity execution, StateDefinition currentState,
                               ExecutionContext context, WorkflowDefinition definition) {
        int sequenceNumber = getNextSequenceNumber(execution.getId());

        while (currentState != null) {
            // Check execution-level timeout
            if (isTimedOut(execution)) {
                markTimedOut(execution, context.getContextVariables(), currentState);
                return;
            }

            // Get the appropriate processor
            StateProcessor processor = processorMap.get(currentState.getType());
            if (processor == null) {
                markFailed(execution, "No processor for state type: " + currentState.getType());
                return;
            }

            // Record history entry (state entry)
            ExecutionHistoryEntity historyEntry = new ExecutionHistoryEntity(
                    execution.getId(), currentState.getId(), currentState.getName(), sequenceNumber);
            historyEntry.setContextSnapshot(serializeContext(context.getContextVariables()));
            historyEntry = historyRepository.save(historyEntry);

            // Process the state
            StateProcessorResult result;
            try {
                result = processor.process(currentState, context);
            } catch (Exception e) {
                logger.error("Error processing state {} in execution {}",
                        currentState.getName(), execution.getId(), e);
                result = StateProcessorResult.failure(e.getMessage());
            }

            // Record state exit
            historyEntry.setExitTime(Instant.now());
            historyEntry.setOutcome(result.getOutcome() != null ? result.getOutcome().getValue() : null);
            if (result.getErrorMessage() != null) {
                historyEntry.setErrorMessage(result.getErrorMessage());
            }
            historyRepository.save(historyEntry);

            // Merge output variables into context
            if (result.getOutputVariables() != null && !result.getOutputVariables().isEmpty()) {
                for (Map.Entry<String, Object> entry : result.getOutputVariables().entrySet()) {
                    context.setVariable(entry.getKey(), entry.getValue());
                }
            }

            // Handle pause (Input/Wait states)
            if (result.isPaused()) {
                execution.setStatus(ExecutionStatus.PAUSED.getValue());
                execution.setCurrentStateId(currentState.getId());
                execution.setContextVariables(serializeContext(context.getContextVariables()));
                executionRepository.save(execution);
                logger.info("Execution {} paused at state {}", execution.getId(), currentState.getName());
                return;
            }

            // Handle End state
            if (currentState.getType() == StateType.END) {
                markCompleted(execution, context.getContextVariables());
                return;
            }

            // Handle failure without error transition
            if (result.getOutcome() == StateOutcome.FAILED) {
                UUID nextStateId = resolveNextState(currentState.getId(),
                        result.getNextTransitionCondition(), definition.getTransitions());
                if (nextStateId == null) {
                    markFailed(execution, result.getErrorMessage());
                    return;
                }
                // Error transition exists, continue to it
                currentState = findStateById(definition, nextStateId);
                execution.setCurrentStateId(nextStateId);
                execution.setContextVariables(serializeContext(context.getContextVariables()));
                executionRepository.save(execution);
                sequenceNumber++;
                continue;
            }

            // Determine next state from transitions + result condition
            UUID nextStateId = resolveNextState(currentState.getId(),
                    result.getNextTransitionCondition(), definition.getTransitions());

            if (nextStateId == null) {
                // No outgoing transition — execution completes
                markCompleted(execution, context.getContextVariables());
                return;
            }

            currentState = findStateById(definition, nextStateId);
            if (currentState == null) {
                markFailed(execution, "Target state not found for transition from: " + nextStateId);
                return;
            }

            execution.setCurrentStateId(currentState.getId());
            execution.setContextVariables(serializeContext(context.getContextVariables()));
            executionRepository.save(execution);
            sequenceNumber++;
        }
    }

    /**
     * Find the start state — the state with no incoming transitions.
     */
    StateDefinition findStartState(WorkflowDefinition definition) {
        if (definition.getStates() == null || definition.getStates().isEmpty()) {
            return null;
        }

        Set<UUID> targetIds = definition.getTransitions() != null
                ? definition.getTransitions().stream()
                .map(TransitionDefinition::getTarget)
                .collect(Collectors.toSet())
                : java.util.Collections.emptySet();

        for (StateDefinition state : definition.getStates()) {
            if (!targetIds.contains(state.getId())) {
                return state;
            }
        }
        return null;
    }

    /**
     * Initialize context variables from the workflow's defined defaults.
     */
    Map<String, Object> initializeContextVariables(List<ContextVariable> variables) {
        Map<String, Object> context = new HashMap<>();
        if (variables != null) {
            for (ContextVariable var : variables) {
                context.put(var.getName(), var.getDefaultValue());
            }
        }
        return context;
    }

    /**
     * Resolve the next state ID based on the current state's outgoing transitions
     * and the condition from the processor result.
     */
    UUID resolveNextState(UUID currentStateId, String condition, List<TransitionDefinition> transitions) {
        if (transitions == null) {
            return null;
        }

        // Find transitions from the current state
        List<TransitionDefinition> outgoing = transitions.stream()
                .filter(t -> currentStateId.equals(t.getSource()))
                .collect(Collectors.toList());

        if (outgoing.isEmpty()) {
            return null;
        }

        // If a condition is specified, find the matching transition
        if (condition != null) {
            for (TransitionDefinition t : outgoing) {
                if (condition.equals(t.getCondition())) {
                    return t.getTarget();
                }
            }
            // Fallback to default (null condition) transition if no match
            for (TransitionDefinition t : outgoing) {
                if (t.getCondition() == null) {
                    return t.getTarget();
                }
            }
            return null;
        }

        // No condition specified — use default (null condition) transition, or first available
        for (TransitionDefinition t : outgoing) {
            if (t.getCondition() == null) {
                return t.getTarget();
            }
        }
        // If no null-condition transition, take the first one
        return outgoing.get(0).getTarget();
    }

    private StateDefinition findStateById(WorkflowDefinition definition, UUID stateId) {
        if (definition.getStates() == null) {
            return null;
        }
        return definition.getStates().stream()
                .filter(s -> stateId.equals(s.getId()))
                .findFirst()
                .orElse(null);
    }

    private boolean isTimedOut(ExecutionEntity execution) {
        if (execution.getStartTime() == null) {
            return false;
        }
        Duration elapsed = Duration.between(execution.getStartTime(), Instant.now());
        return elapsed.getSeconds() >= execution.getMaxDurationSeconds();
    }

    private void markCompleted(ExecutionEntity execution, Map<String, Object> contextVariables) {
        execution.setStatus(ExecutionStatus.COMPLETED.getValue());
        execution.setEndTime(Instant.now());
        execution.setContextVariables(serializeContext(contextVariables));
        executionRepository.save(execution);
        logger.info("Execution {} completed", execution.getId());
    }

    private void markFailed(ExecutionEntity execution, String errorMessage) {
        execution.setStatus(ExecutionStatus.FAILED.getValue());
        execution.setEndTime(Instant.now());
        execution.setErrorMessage(errorMessage);
        executionRepository.save(execution);
        logger.error("Execution {} failed: {}", execution.getId(), errorMessage);
    }

    private void markTimedOut(ExecutionEntity execution, Map<String, Object> contextVariables,
                              StateDefinition currentState) {
        execution.setStatus(ExecutionStatus.FAILED.getValue());
        execution.setEndTime(Instant.now());
        execution.setErrorMessage("Execution timed out after " + execution.getMaxDurationSeconds() + " seconds");
        execution.setContextVariables(serializeContext(contextVariables));
        executionRepository.save(execution);

        // Record timeout in history
        int seq = getNextSequenceNumber(execution.getId());
        ExecutionHistoryEntity historyEntry = new ExecutionHistoryEntity(
                execution.getId(), currentState.getId(), currentState.getName(), seq);
        historyEntry.setOutcome(StateOutcome.TIMED_OUT.getValue());
        historyEntry.setExitTime(Instant.now());
        historyEntry.setErrorMessage("Execution-level timeout exceeded");
        historyRepository.save(historyEntry);

        logger.error("Execution {} timed out at state {}", execution.getId(), currentState.getName());
    }

    private int getNextSequenceNumber(UUID executionId) {
        List<ExecutionHistoryEntity> history = historyRepository
                .findByExecutionIdOrderBySequenceNumberAsc(executionId);
        if (history.isEmpty()) {
            return 1;
        }
        return history.get(history.size() - 1).getSequenceNumber() + 1;
    }

    private String serializeContext(Map<String, Object> contextVariables) {
        try {
            return objectMapper.writeValueAsString(contextVariables);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize context variables", e);
            return "{}";
        }
    }

    private Map<String, Object> deserializeContext(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            logger.error("Failed to deserialize context variables", e);
            return new HashMap<>();
        }
    }
}

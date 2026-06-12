package com.chatbot.workflow.engine;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chatbot.workflow.model.ContextVariable;
import com.chatbot.workflow.model.ExecutionStatus;
import com.chatbot.workflow.model.StateDefinition;
import com.chatbot.workflow.model.StateType;
import com.chatbot.workflow.model.TransitionDefinition;
import com.chatbot.workflow.model.WorkflowDefinition;
import com.chatbot.workflow.model.WorkflowMetadata;
import com.chatbot.workflow.repository.ExecutionEntity;
import com.chatbot.workflow.repository.ExecutionHistoryEntity;
import com.chatbot.workflow.repository.ExecutionHistoryRepository;
import com.chatbot.workflow.repository.ExecutionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Unit tests for WorkflowEngine verifying:
 * - Execution initializes with correct defaults
 * - End state completes execution
 * - Execution timeout halts execution
 */
class WorkflowEngineTest {

    private WorkflowEngine engine;
    private ExecutionRepository executionRepository;
    private ExecutionHistoryRepository historyRepository;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        executionRepository = mock(ExecutionRepository.class);
        historyRepository = mock(ExecutionHistoryRepository.class);
        objectMapper = new ObjectMapper();

        List<StateProcessor> processors = Arrays.asList(
                new EndStateProcessor(),
                new ApiCallStateProcessor(new com.chatbot.workflow.service.ContextVariableService()),
                new ConditionStateProcessor(),
                new ResponseStateProcessor(),
                new InputStateProcessor(),
                new WaitStateProcessor(),
                new ParallelStateProcessor()
        );

        engine = new WorkflowEngine(processors, executionRepository, historyRepository, objectMapper);
    }

    @Test
    void startExecution_initializesContextVariablesWithDefaults() {
        // Arrange
        UUID workflowId = UUID.randomUUID();
        UUID startStateId = UUID.randomUUID();
        UUID endStateId = UUID.randomUUID();
        UUID transitionId = UUID.randomUUID();

        StateDefinition startState = new StateDefinition(
                startStateId, StateType.RESPONSE, "Start", null, null, null, null);
        StateDefinition endState = new StateDefinition(
                endStateId, StateType.END, "End", null, null, null, null);
        TransitionDefinition transition = new TransitionDefinition(
                transitionId, startStateId, endStateId, null);

        List<ContextVariable> contextVars = Arrays.asList(
                new ContextVariable("userName", "default_user"),
                new ContextVariable("counter", 0),
                new ContextVariable("message", null)
        );

        WorkflowDefinition definition = new WorkflowDefinition(
                new WorkflowMetadata("Test", null, 1, null, null),
                Arrays.asList(startState, endState),
                Collections.singletonList(transition),
                contextVars
        );

        // Mock repository save to return entity with ID
        when(executionRepository.save(any(ExecutionEntity.class))).thenAnswer(invocation -> {
            ExecutionEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(UUID.randomUUID());
            }
            return entity;
        });
        when(historyRepository.save(any(ExecutionHistoryEntity.class))).thenAnswer(invocation -> {
            ExecutionHistoryEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(UUID.randomUUID());
            }
            return entity;
        });
        when(historyRepository.findByExecutionIdOrderBySequenceNumberAsc(any()))
                .thenReturn(Collections.emptyList());

        // Act
        UUID executionId = engine.startExecution(workflowId, 1, definition);

        // Assert
        assertNotNull(executionId);

        // Verify the execution entity was saved with correct context variables
        ArgumentCaptor<ExecutionEntity> captor = ArgumentCaptor.forClass(ExecutionEntity.class);
        verify(executionRepository, atLeastOnce()).save(captor.capture());

        // The first save is the initial creation with RUNNING status and context defaults
        ExecutionEntity firstSave = captor.getAllValues().get(0);
        assertEquals(workflowId, firstSave.getWorkflowId());
        assertEquals(1, firstSave.getWorkflowVersion());

        // Verify context variables contain defaults in any of the saves (they persist through)
        boolean foundDefaults = false;
        for (ExecutionEntity saved : captor.getAllValues()) {
            String contextJson = saved.getContextVariables();
            if (contextJson.contains("userName") && contextJson.contains("default_user")
                    && contextJson.contains("counter")) {
                foundDefaults = true;
                break;
            }
        }
        assertTrue(foundDefaults, "Context variables should contain defaults");
    }

    @Test
    void startExecution_endStateCompletesExecution() {
        // Arrange: workflow with single End state (start state = End state)
        UUID workflowId = UUID.randomUUID();
        UUID endStateId = UUID.randomUUID();

        StateDefinition endState = new StateDefinition(
                endStateId, StateType.END, "End", null, null, null, null);

        WorkflowDefinition definition = new WorkflowDefinition(
                new WorkflowMetadata("EndOnly", null, 1, null, null),
                Collections.singletonList(endState),
                Collections.emptyList(),
                null
        );

        when(executionRepository.save(any(ExecutionEntity.class))).thenAnswer(invocation -> {
            ExecutionEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(UUID.randomUUID());
            }
            return entity;
        });
        when(historyRepository.save(any(ExecutionHistoryEntity.class))).thenAnswer(invocation -> {
            ExecutionHistoryEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(UUID.randomUUID());
            }
            return entity;
        });
        when(historyRepository.findByExecutionIdOrderBySequenceNumberAsc(any()))
                .thenReturn(Collections.emptyList());

        // Act
        UUID executionId = engine.startExecution(workflowId, 1, definition);

        // Assert
        assertNotNull(executionId);

        ArgumentCaptor<ExecutionEntity> captor = ArgumentCaptor.forClass(ExecutionEntity.class);
        verify(executionRepository, atLeastOnce()).save(captor.capture());

        // The last save should mark the execution as completed
        List<ExecutionEntity> allSaves = captor.getAllValues();
        ExecutionEntity lastSave = allSaves.get(allSaves.size() - 1);
        assertEquals(ExecutionStatus.COMPLETED.getValue(), lastSave.getStatus());
        assertNotNull(lastSave.getEndTime());
    }

    @Test
    void startExecution_timeoutHaltsExecution() {
        // Arrange: workflow where start time is far in the past so timeout triggers immediately
        UUID workflowId = UUID.randomUUID();
        UUID stateId = UUID.randomUUID();

        StateDefinition state = new StateDefinition(
                stateId, StateType.RESPONSE, "LongRunning", null, null, null, null);

        WorkflowDefinition definition = new WorkflowDefinition(
                new WorkflowMetadata("TimeoutTest", null, 1, null, null),
                Collections.singletonList(state),
                Collections.emptyList(),
                null
        );

        // Mock save to set start time far in the past (simulating timeout)
        when(executionRepository.save(any(ExecutionEntity.class))).thenAnswer(invocation -> {
            ExecutionEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(UUID.randomUUID());
                // Set start time to 2 hours ago so timeout (3600s) is exceeded
                entity.setStartTime(Instant.now().minusSeconds(7200));
            }
            return entity;
        });
        when(historyRepository.save(any(ExecutionHistoryEntity.class))).thenAnswer(invocation -> {
            ExecutionHistoryEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(UUID.randomUUID());
            }
            return entity;
        });
        when(historyRepository.findByExecutionIdOrderBySequenceNumberAsc(any()))
                .thenReturn(Collections.emptyList());

        // Act
        UUID executionId = engine.startExecution(workflowId, 1, definition);

        // Assert
        assertNotNull(executionId);

        ArgumentCaptor<ExecutionEntity> captor = ArgumentCaptor.forClass(ExecutionEntity.class);
        verify(executionRepository, atLeastOnce()).save(captor.capture());

        // The last save should mark execution as failed due to timeout
        List<ExecutionEntity> allSaves = captor.getAllValues();
        ExecutionEntity lastSave = allSaves.get(allSaves.size() - 1);
        assertEquals(ExecutionStatus.FAILED.getValue(), lastSave.getStatus());
        assertNotNull(lastSave.getErrorMessage());
        assertTrue(lastSave.getErrorMessage().contains("timed out"));
    }

    @Test
    void startExecution_multiStateWorkflowProcessesInSequence() {
        // Arrange: Response → API_Call → End
        UUID workflowId = UUID.randomUUID();
        UUID responseStateId = UUID.randomUUID();
        UUID apiCallStateId = UUID.randomUUID();
        UUID endStateId = UUID.randomUUID();

        StateDefinition responseState = new StateDefinition(
                responseStateId, StateType.RESPONSE, "Respond", null, null, null, null);
        StateDefinition apiCallState = new StateDefinition(
                apiCallStateId, StateType.API_CALL, "CallAPI", null, null, null, null);
        StateDefinition endState = new StateDefinition(
                endStateId, StateType.END, "End", null, null, null, null);

        TransitionDefinition t1 = new TransitionDefinition(
                UUID.randomUUID(), responseStateId, apiCallStateId, null);
        TransitionDefinition t2 = new TransitionDefinition(
                UUID.randomUUID(), apiCallStateId, endStateId, null);

        WorkflowDefinition definition = new WorkflowDefinition(
                new WorkflowMetadata("MultiState", null, 1, null, null),
                Arrays.asList(responseState, apiCallState, endState),
                Arrays.asList(t1, t2),
                null
        );

        when(executionRepository.save(any(ExecutionEntity.class))).thenAnswer(invocation -> {
            ExecutionEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(UUID.randomUUID());
                entity.setStartTime(Instant.now());
            }
            return entity;
        });
        when(historyRepository.save(any(ExecutionHistoryEntity.class))).thenAnswer(invocation -> {
            ExecutionHistoryEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(UUID.randomUUID());
            }
            return entity;
        });
        when(historyRepository.findByExecutionIdOrderBySequenceNumberAsc(any()))
                .thenReturn(Collections.emptyList());

        // Act
        UUID executionId = engine.startExecution(workflowId, 1, definition);

        // Assert
        assertNotNull(executionId);

        // Should have recorded 6 history saves: 2 per state (entry + exit) for 3 states
        verify(historyRepository, times(6)).save(any(ExecutionHistoryEntity.class));

        // Final execution state should be COMPLETED
        ArgumentCaptor<ExecutionEntity> captor = ArgumentCaptor.forClass(ExecutionEntity.class);
        verify(executionRepository, atLeastOnce()).save(captor.capture());
        List<ExecutionEntity> allSaves = captor.getAllValues();
        ExecutionEntity lastSave = allSaves.get(allSaves.size() - 1);
        assertEquals(ExecutionStatus.COMPLETED.getValue(), lastSave.getStatus());
    }

    @Test
    void startExecution_inputStatePausesExecution() {
        // Arrange: Input → End
        UUID workflowId = UUID.randomUUID();
        UUID inputStateId = UUID.randomUUID();
        UUID endStateId = UUID.randomUUID();

        StateDefinition inputState = new StateDefinition(
                inputStateId, StateType.INPUT, "GetInput", null, null, null, null);
        StateDefinition endState = new StateDefinition(
                endStateId, StateType.END, "End", null, null, null, null);

        TransitionDefinition t1 = new TransitionDefinition(
                UUID.randomUUID(), inputStateId, endStateId, null);

        WorkflowDefinition definition = new WorkflowDefinition(
                new WorkflowMetadata("InputTest", null, 1, null, null),
                Arrays.asList(inputState, endState),
                Collections.singletonList(t1),
                null
        );

        when(executionRepository.save(any(ExecutionEntity.class))).thenAnswer(invocation -> {
            ExecutionEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(UUID.randomUUID());
                entity.setStartTime(Instant.now());
            }
            return entity;
        });
        when(historyRepository.save(any(ExecutionHistoryEntity.class))).thenAnswer(invocation -> {
            ExecutionHistoryEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(UUID.randomUUID());
            }
            return entity;
        });
        when(historyRepository.findByExecutionIdOrderBySequenceNumberAsc(any()))
                .thenReturn(Collections.emptyList());

        // Act
        UUID executionId = engine.startExecution(workflowId, 1, definition);

        // Assert
        assertNotNull(executionId);

        ArgumentCaptor<ExecutionEntity> captor = ArgumentCaptor.forClass(ExecutionEntity.class);
        verify(executionRepository, atLeastOnce()).save(captor.capture());

        List<ExecutionEntity> allSaves = captor.getAllValues();
        ExecutionEntity lastSave = allSaves.get(allSaves.size() - 1);
        assertEquals(ExecutionStatus.PAUSED.getValue(), lastSave.getStatus());
        assertEquals(inputStateId, lastSave.getCurrentStateId());
    }

    @Test
    void findStartState_returnsStateWithNoIncomingTransitions() {
        UUID stateAId = UUID.randomUUID();
        UUID stateBId = UUID.randomUUID();
        UUID stateCId = UUID.randomUUID();

        StateDefinition stateA = new StateDefinition(
                stateAId, StateType.RESPONSE, "A", null, null, null, null);
        StateDefinition stateB = new StateDefinition(
                stateBId, StateType.CONDITION, "B", null, null, null, null);
        StateDefinition stateC = new StateDefinition(
                stateCId, StateType.END, "C", null, null, null, null);

        // A → B → C (so A is the start state — no incoming transitions)
        TransitionDefinition t1 = new TransitionDefinition(UUID.randomUUID(), stateAId, stateBId, null);
        TransitionDefinition t2 = new TransitionDefinition(UUID.randomUUID(), stateBId, stateCId, null);

        WorkflowDefinition definition = new WorkflowDefinition(
                null,
                Arrays.asList(stateA, stateB, stateC),
                Arrays.asList(t1, t2),
                null
        );

        StateDefinition startState = engine.findStartState(definition);
        assertNotNull(startState);
        assertEquals(stateAId, startState.getId());
        assertEquals("A", startState.getName());
    }

    @Test
    void initializeContextVariables_setsDefaults() {
        List<ContextVariable> vars = Arrays.asList(
                new ContextVariable("name", "Alice"),
                new ContextVariable("count", 42),
                new ContextVariable("flag", true),
                new ContextVariable("empty", null)
        );

        Map<String, Object> result = engine.initializeContextVariables(vars);

        assertEquals(4, result.size());
        assertEquals("Alice", result.get("name"));
        assertEquals(42, result.get("count"));
        assertEquals(true, result.get("flag"));
        assertNull(result.get("empty"));
    }

    @Test
    void initializeContextVariables_emptyListReturnsEmptyMap() {
        Map<String, Object> result = engine.initializeContextVariables(Collections.emptyList());
        assertTrue(result.isEmpty());
    }

    @Test
    void initializeContextVariables_nullReturnsEmptyMap() {
        Map<String, Object> result = engine.initializeContextVariables(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void resolveNextState_findsDefaultTransition() {
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        TransitionDefinition transition = new TransitionDefinition(
                UUID.randomUUID(), sourceId, targetId, null);

        UUID result = engine.resolveNextState(sourceId, null, Collections.singletonList(transition));
        assertEquals(targetId, result);
    }

    @Test
    void resolveNextState_findsConditionTransition() {
        UUID sourceId = UUID.randomUUID();
        UUID trueTargetId = UUID.randomUUID();
        UUID falseTargetId = UUID.randomUUID();

        TransitionDefinition trueTransition = new TransitionDefinition(
                UUID.randomUUID(), sourceId, trueTargetId, "true");
        TransitionDefinition falseTransition = new TransitionDefinition(
                UUID.randomUUID(), sourceId, falseTargetId, "false");

        UUID result = engine.resolveNextState(sourceId, "true",
                Arrays.asList(trueTransition, falseTransition));
        assertEquals(trueTargetId, result);

        result = engine.resolveNextState(sourceId, "false",
                Arrays.asList(trueTransition, falseTransition));
        assertEquals(falseTargetId, result);
    }

    @Test
    void resolveNextState_returnsNullWhenNoTransitions() {
        UUID sourceId = UUID.randomUUID();

        UUID result = engine.resolveNextState(sourceId, null, Collections.emptyList());
        assertNull(result);
    }

    @Test
    void resolveNextState_returnsNullForNullTransitionList() {
        UUID sourceId = UUID.randomUUID();
        UUID result = engine.resolveNextState(sourceId, null, null);
        assertNull(result);
    }

    @Test
    void startExecution_throwsForNoStartState() {
        UUID workflowId = UUID.randomUUID();

        WorkflowDefinition definition = new WorkflowDefinition(
                new WorkflowMetadata("Empty", null, 1, null, null),
                Collections.emptyList(),
                Collections.emptyList(),
                null
        );

        assertThrows(IllegalArgumentException.class, () ->
                engine.startExecution(workflowId, 1, definition));
    }
}

package com.xpressbees.chatbot.processor;

import com.xpressbees.chatbot.dto.NodeProcessingResult;
import com.xpressbees.chatbot.dto.NodeProcessingResult.Action;
import com.xpressbees.chatbot.entity.ChatSession;
import com.xpressbees.chatbot.service.ConditionEvaluator;
import com.xpressbees.chatbot.service.PlaceholderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DecisionNodeProcessor.
 *
 * Validates: Requirements 1.1, 1.2, 1.3, 2.1, 2.2, 2.3, 2.4, 3.1, 3.2, 3.3, 3.4, 3.5,
 *            4.1, 4.2, 4.3, 5.1, 5.2, 6.4, 6.6, 7.1, 7.2, 8.2, 8.3
 */
@ExtendWith(MockitoExtension.class)
class DecisionNodeProcessorTest {

    @Mock private ConditionEvaluator conditionEvaluator;
    @Mock private PlaceholderService placeholderService;

    private DecisionNodeProcessor processor;

    private static final String SESSION_ID = "session-123";
    private static final Long WORKFLOW_ID = 1L;
    private static final String DECISION_NODE_ID = "node-decision-1";

    @BeforeEach
    void setUp() {
        processor = new DecisionNodeProcessor(conditionEvaluator);
    }

    // ======================== Helper Methods ========================

    private ChatSession createSession() {
        ChatSession session = new ChatSession();
        session.setSessionId(SESSION_ID);
        session.setWorkflowId(WORKFLOW_ID);
        session.setContext(new HashMap<>());
        session.setStatus("active");
        return session;
    }

    private Map<String, Object> createDecisionNode(String nodeId) {
        Map<String, Object> node = new HashMap<>();
        node.put("id", nodeId);
        node.put("type", "decision");
        return node;
    }

    private Map<String, Object> createTransition(String sourceNodeId, String targetNodeId, String condition) {
        Map<String, Object> transition = new HashMap<>();
        transition.put("sourceNodeId", sourceNodeId);
        transition.put("targetNodeId", targetNodeId);
        if (condition != null) {
            transition.put("condition", condition);
        }
        return transition;
    }

    private Map<String, Object> createWorkflowJson(List<Map<String, Object>> transitions) {
        Map<String, Object> workflowJson = new HashMap<>();
        workflowJson.put("transitions", transitions);
        return workflowJson;
    }

    // ======================== canHandle Tests ========================

    @Test
    @DisplayName("canHandle returns true for node with type 'decision'")
    void canHandle_returnsTrue_forDecisionType() {
        Map<String, Object> node = createDecisionNode(DECISION_NODE_ID);

        assertThat(processor.canHandle(node)).isTrue();
    }

    @Test
    @DisplayName("canHandle returns false for node with type 'message'")
    void canHandle_returnsFalse_forMessageType() {
        Map<String, Object> node = new HashMap<>();
        node.put("type", "message");

        assertThat(processor.canHandle(node)).isFalse();
    }

    @Test
    @DisplayName("canHandle returns false for node with type 'input'")
    void canHandle_returnsFalse_forInputType() {
        Map<String, Object> node = new HashMap<>();
        node.put("type", "input");

        assertThat(processor.canHandle(node)).isFalse();
    }

    @Test
    @DisplayName("canHandle returns false for node with null type")
    void canHandle_returnsFalse_forNullType() {
        Map<String, Object> node = new HashMap<>();
        node.put("type", null);

        assertThat(processor.canHandle(node)).isFalse();
    }

    @Test
    @DisplayName("canHandle returns false for node with missing type key")
    void canHandle_returnsFalse_forMissingTypeKey() {
        Map<String, Object> node = new HashMap<>();
        node.put("id", "some-node");

        assertThat(processor.canHandle(node)).isFalse();
    }

    @Test
    @DisplayName("canHandle returns false for type 'Decision' (case-sensitive)")
    void canHandle_returnsFalse_forWrongCase() {
        Map<String, Object> node = new HashMap<>();
        node.put("type", "Decision");

        assertThat(processor.canHandle(node)).isFalse();
    }

    // ======================== Null workflowJson Tests ========================

    @Test
    @DisplayName("process returns ERROR when workflowJson is null")
    void process_returnsError_whenWorkflowJsonIsNull() {
        ChatSession session = createSession();
        Map<String, Object> node = createDecisionNode(DECISION_NODE_ID);

        NodeProcessingResult result = processor.process(node, session, placeholderService, null);

        assertThat(result.getAction()).isEqualTo(Action.ERROR);
        assertThat(result.getErrorMessage()).isEqualTo("Workflow is no longer available");
    }

    // ======================== Zero Outgoing Transitions Tests ========================

    @Test
    @DisplayName("process returns ERROR when zero outgoing transitions")
    void process_returnsError_whenZeroOutgoingTransitions() {
        ChatSession session = createSession();
        Map<String, Object> node = createDecisionNode(DECISION_NODE_ID);

        // Create workflow with transitions that don't match the decision node
        List<Map<String, Object>> transitions = List.of(
                createTransition("other-node", "target-1", "x == 1")
        );
        Map<String, Object> workflowJson = createWorkflowJson(transitions);

        NodeProcessingResult result = processor.process(node, session, placeholderService, workflowJson);

        assertThat(result.getAction()).isEqualTo(Action.ERROR);
        assertThat(result.getErrorMessage()).isEqualTo("Decision node has no outgoing transitions");
    }

    // ======================== Happy Path: Single Transition Match ========================

    @Test
    @DisplayName("process stores _targetNodeId and returns CONTINUE on single match (happy path)")
    void process_storesTargetAndContinues_onSingleMatch() {
        ChatSession session = createSession();
        Map<String, Object> node = createDecisionNode(DECISION_NODE_ID);

        List<Map<String, Object>> transitions = List.of(
                createTransition(DECISION_NODE_ID, "target-node-1", "status == active")
        );
        Map<String, Object> workflowJson = createWorkflowJson(transitions);
        when(conditionEvaluator.evaluate(eq("status == active"), anyMap())).thenReturn(true);

        NodeProcessingResult result = processor.process(node, session, placeholderService, workflowJson);

        assertThat(result.getAction()).isEqualTo(Action.CONTINUE);
        assertThat(result.getResponse()).isNull();
        assertThat(session.getContext()).containsEntry("_targetNodeId", "target-node-1");
    }

    // ======================== Multiple Transitions: Second Matches ========================

    @Test
    @DisplayName("process evaluates in order and picks second matching transition")
    void process_picksSecondTransition_whenFirstDoesNotMatch() {
        ChatSession session = createSession();
        Map<String, Object> node = createDecisionNode(DECISION_NODE_ID);

        List<Map<String, Object>> transitions = List.of(
                createTransition(DECISION_NODE_ID, "target-A", "status == active"),
                createTransition(DECISION_NODE_ID, "target-B", "status == inactive"),
                createTransition(DECISION_NODE_ID, "target-C", "status == pending")
        );
        Map<String, Object> workflowJson = createWorkflowJson(transitions);
        when(conditionEvaluator.evaluate(eq("status == active"), anyMap())).thenReturn(false);
        when(conditionEvaluator.evaluate(eq("status == inactive"), anyMap())).thenReturn(true);

        NodeProcessingResult result = processor.process(node, session, placeholderService, workflowJson);

        assertThat(result.getAction()).isEqualTo(Action.CONTINUE);
        assertThat(result.getResponse()).isNull();
        assertThat(session.getContext()).containsEntry("_targetNodeId", "target-B");

        // Third condition should NOT be evaluated (first-match-wins)
        verify(conditionEvaluator, never()).evaluate(eq("status == pending"), anyMap());
    }

    // ======================== Transition with Null Condition is Skipped ========================

    @Test
    @DisplayName("process skips transition with null condition")
    void process_skipsTransition_withNullCondition() {
        ChatSession session = createSession();
        Map<String, Object> node = createDecisionNode(DECISION_NODE_ID);

        // First transition has null condition (no "condition" key), second has valid condition
        Map<String, Object> transitionNoCondition = createTransition(DECISION_NODE_ID, "target-skip", null);
        Map<String, Object> transitionWithCondition = createTransition(DECISION_NODE_ID, "target-match", "x == 1");

        List<Map<String, Object>> transitions = List.of(transitionNoCondition, transitionWithCondition);
        Map<String, Object> workflowJson = createWorkflowJson(transitions);
        when(conditionEvaluator.evaluate(eq("x == 1"), anyMap())).thenReturn(true);

        NodeProcessingResult result = processor.process(node, session, placeholderService, workflowJson);

        assertThat(result.getAction()).isEqualTo(Action.CONTINUE);
        assertThat(session.getContext()).containsEntry("_targetNodeId", "target-match");
        // Null-condition transition should NOT be evaluated
        verify(conditionEvaluator, never()).evaluate(isNull(), anyMap());
    }

    // ======================== Matched Transition with Null targetNodeId ========================

    @Test
    @DisplayName("process returns ERROR when matched transition has null targetNodeId")
    void process_returnsError_whenMatchedTransitionHasNullTarget() {
        ChatSession session = createSession();
        Map<String, Object> node = createDecisionNode(DECISION_NODE_ID);

        // Transition with valid condition but null targetNodeId
        Map<String, Object> transition = new HashMap<>();
        transition.put("sourceNodeId", DECISION_NODE_ID);
        transition.put("targetNodeId", null);
        transition.put("condition", "status == active");

        List<Map<String, Object>> transitions = List.of(transition);
        Map<String, Object> workflowJson = createWorkflowJson(transitions);
        when(conditionEvaluator.evaluate(eq("status == active"), anyMap())).thenReturn(true);

        NodeProcessingResult result = processor.process(node, session, placeholderService, workflowJson);

        assertThat(result.getAction()).isEqualTo(Action.ERROR);
        assertThat(result.getErrorMessage()).isEqualTo("Matched transition has no target node");
    }

    // ======================== No Condition Matches ========================

    @Test
    @DisplayName("process returns ERROR when no condition matches")
    void process_returnsError_whenNoConditionMatches() {
        ChatSession session = createSession();
        Map<String, Object> node = createDecisionNode(DECISION_NODE_ID);

        List<Map<String, Object>> transitions = List.of(
                createTransition(DECISION_NODE_ID, "target-A", "status == active"),
                createTransition(DECISION_NODE_ID, "target-B", "status == inactive")
        );
        Map<String, Object> workflowJson = createWorkflowJson(transitions);
        when(conditionEvaluator.evaluate(eq("status == active"), anyMap())).thenReturn(false);
        when(conditionEvaluator.evaluate(eq("status == inactive"), anyMap())).thenReturn(false);

        NodeProcessingResult result = processor.process(node, session, placeholderService, workflowJson);

        assertThat(result.getAction()).isEqualTo(Action.ERROR);
        assertThat(result.getErrorMessage()).isEqualTo("No matching condition found for decision node");
        assertThat(session.getContext()).doesNotContainKey("_targetNodeId");
    }

    // ======================== Annotation Verification ========================

    @Test
    @DisplayName("DecisionNodeProcessor has @Order(5) annotation")
    void class_hasOrderAnnotation_withValue5() {
        Order orderAnnotation = DecisionNodeProcessor.class.getAnnotation(Order.class);

        assertThat(orderAnnotation).isNotNull();
        assertThat(orderAnnotation.value()).isEqualTo(5);
    }

    @Test
    @DisplayName("DecisionNodeProcessor has @Component annotation")
    void class_hasComponentAnnotation() {
        Component componentAnnotation = DecisionNodeProcessor.class.getAnnotation(Component.class);

        assertThat(componentAnnotation).isNotNull();
    }
}

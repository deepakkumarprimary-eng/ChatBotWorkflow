package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.entity.ChatSession;
import com.xpressbees.chatbot.entity.Workflow;
import com.xpressbees.chatbot.processor.InputNodeProcessor;
import com.xpressbees.chatbot.processor.MessageNodeProcessor;
import com.xpressbees.chatbot.processor.NodeProcessor;
import com.xpressbees.chatbot.repository.ChatSessionRepository;
import com.xpressbees.chatbot.repository.WorkflowRepository;
import net.jqwik.api.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property 1: Bug Condition — Input Node Stores Reply Under Hardcoded "mobile_no" Key
 *
 * This test encodes the EXPECTED (correct) behavior: user replies should be stored
 * under the configured variable name from _inputVariableName context key.
 *
 * On UNFIXED code, this test MUST FAIL — proving the bug exists because
 * handleInputNodeResume() always stores under "mobile_no" regardless of config.
 *
 * Validates: Requirements 2.1, 2.2, 2.4
 */
class InputNodeVariableNamePropertyTest {

    /**
     * Property-based test: For all generated variableName (non-empty, != "mobile_no")
     * and all generated user reply strings, after calling handleUserInput on a session
     * with currentNodeType="input" and context containing _inputVariableName=variableName:
     *   - session.getContext().get(variableName) should equal message
     *   - session.getContext().get("_inputVariableName") should be null (cleaned up)
     *   - session.getContext().get("mobile_no") should NOT equal message
     *
     * Validates: Requirements 2.1, 2.4
     */
    @Property(tries = 50)
    @Tag("Feature: input-node-variable-name-fix, Property 1: Bug Condition")
    void replyStoredUnderConfiguredVariableName(
            @ForAll("variableNames") String variableName,
            @ForAll("userReplies") String message) {

        // Setup mocks
        WorkflowRepository workflowRepository = mock(WorkflowRepository.class);
        ChatSessionRepository chatSessionRepository = mock(ChatSessionRepository.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        PlaceholderService placeholderService = new PlaceholderService();
        List<NodeProcessor> processors = List.of(new InputNodeProcessor(), new MessageNodeProcessor());

        WorkflowExecutionServiceImpl service = new WorkflowExecutionServiceImpl(
                workflowRepository, chatSessionRepository, processors, placeholderService, messagingTemplate);

        String sessionId = "test-session-" + UUID.randomUUID();

        // Create session with currentNodeType="input" and _inputVariableName in context
        ChatSession session = new ChatSession();
        session.setSessionId(sessionId);
        session.setWorkflowId(1L);
        session.setCurrentNodeId("input-node-1");
        session.setCurrentType("state");
        session.setCurrentNodeType("input");
        session.setStatus("active");

        Map<String, Object> context = new HashMap<>();
        context.put("_inputVariableName", variableName);
        session.setContext(context);

        // Mock findBySessionId to return the session
        when(chatSessionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(session));
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(inv -> inv.getArgument(0));

        // Mock workflow with a next node (message node) so the flow doesn't error out
        Workflow workflow = new Workflow();
        workflow.setId(1L);
        workflow.setName("Test Workflow");

        Map<String, Object> nextNode = new HashMap<>();
        nextNode.put("id", "msg-node-2");
        nextNode.put("name", "Thank you");
        nextNode.put("type", "state");
        // No nodeType in config so MessageNodeProcessor handles it directly
        nextNode.put("config", new HashMap<>());

        Map<String, Object> inputNode = new HashMap<>();
        inputNode.put("id", "input-node-1");
        inputNode.put("name", "Enter value");
        inputNode.put("type", "state");
        inputNode.put("config", Map.of("nodeType", "input", "variableName", variableName));

        Map<String, Object> transition = new HashMap<>();
        transition.put("sourceNodeId", "input-node-1");
        transition.put("targetNodeId", "msg-node-2");

        Map<String, Object> workflowJson = new HashMap<>();
        workflowJson.put("nodes", List.of(inputNode, nextNode));
        workflowJson.put("transitions", List.of(transition));
        workflow.setWorkflowJson(workflowJson);

        when(workflowRepository.findById(1L)).thenReturn(Optional.of(workflow));

        // Act: call handleUserInput which delegates to handleInputNodeResume
        service.handleUserInput(sessionId, message);

        // Assert: reply should be stored under the configured variable name
        Map<String, Object> resultContext = session.getContext();

        assertThat(resultContext.get(variableName))
                .as("Reply should be stored under key '%s' but was not found", variableName)
                .isEqualTo(message);

        assertThat(resultContext.get("_inputVariableName"))
                .as("Temporary key '_inputVariableName' should be cleaned up after storage")
                .isNull();

        assertThat(resultContext.get("mobile_no"))
                .as("Reply should NOT be stored under hardcoded 'mobile_no' key when variableName='%s'", variableName)
                .isNotEqualTo(message);
    }

    /**
     * Deterministic case: variableName = "email", reply = "user@test.com"
     * Expected: context has key "email" with value "user@test.com"
     *
     * Validates: Requirements 2.1
     */
    @Example
    @Tag("Feature: input-node-variable-name-fix, Property 1: Bug Condition")
    void emailVariableStoredCorrectly() {
        assertVariableStoredCorrectly("email", "user@test.com");
    }

    /**
     * Deterministic case: variableName = "order_id", reply = "ORD-999"
     * Expected: context has key "order_id" with value "ORD-999"
     *
     * Validates: Requirements 2.1
     */
    @Example
    @Tag("Feature: input-node-variable-name-fix, Property 1: Bug Condition")
    void orderIdVariableStoredCorrectly() {
        assertVariableStoredCorrectly("order_id", "ORD-999");
    }

    /**
     * Deterministic case: No _inputVariableName in context (fallback case)
     * with node id "node-abc" → assert context has key "node-abc"
     *
     * Validates: Requirements 2.2
     */
    @Example
    @Tag("Feature: input-node-variable-name-fix, Property 1: Bug Condition")
    void fallbackToNodeIdWhenNoInputVariableName() {
        // Setup mocks
        WorkflowRepository workflowRepository = mock(WorkflowRepository.class);
        ChatSessionRepository chatSessionRepository = mock(ChatSessionRepository.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        PlaceholderService placeholderService = new PlaceholderService();
        List<NodeProcessor> processors = List.of(new InputNodeProcessor(), new MessageNodeProcessor());

        WorkflowExecutionServiceImpl service = new WorkflowExecutionServiceImpl(
                workflowRepository, chatSessionRepository, processors, placeholderService, messagingTemplate);

        String sessionId = "test-session-fallback";
        String nodeId = "node-abc";
        String message = "hello-world";

        // Create session WITHOUT _inputVariableName in context
        ChatSession session = new ChatSession();
        session.setSessionId(sessionId);
        session.setWorkflowId(1L);
        session.setCurrentNodeId(nodeId);
        session.setCurrentType("state");
        session.setCurrentNodeType("input");
        session.setStatus("active");
        session.setContext(new HashMap<>());  // No _inputVariableName key

        when(chatSessionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(session));
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(inv -> inv.getArgument(0));

        // Workflow with node config that has variableName matching the fallback expectation
        Workflow workflow = new Workflow();
        workflow.setId(1L);
        workflow.setName("Test Workflow");

        Map<String, Object> inputNode = new HashMap<>();
        inputNode.put("id", nodeId);
        inputNode.put("name", "Enter value");
        inputNode.put("type", "state");
        // No variableName in config — should fall back to node id
        inputNode.put("config", Map.of("nodeType", "input"));

        Map<String, Object> nextNode = new HashMap<>();
        nextNode.put("id", "msg-node-2");
        nextNode.put("name", "Thank you");
        nextNode.put("type", "state");
        nextNode.put("config", new HashMap<>());

        Map<String, Object> transition = new HashMap<>();
        transition.put("sourceNodeId", nodeId);
        transition.put("targetNodeId", "msg-node-2");

        Map<String, Object> workflowJson = new HashMap<>();
        workflowJson.put("nodes", List.of(inputNode, nextNode));
        workflowJson.put("transitions", List.of(transition));
        workflow.setWorkflowJson(workflowJson);

        when(workflowRepository.findById(1L)).thenReturn(Optional.of(workflow));

        // Act
        service.handleUserInput(sessionId, message);

        // Assert: reply should be stored under the node id as fallback
        Map<String, Object> resultContext = session.getContext();

        assertThat(resultContext.get(nodeId))
                .as("Reply should be stored under node id '%s' when _inputVariableName is absent", nodeId)
                .isEqualTo(message);

        assertThat(resultContext.get("mobile_no"))
                .as("Reply should NOT be stored under hardcoded 'mobile_no' key")
                .isNotEqualTo(message);
    }

    /**
     * Helper method for deterministic test cases that test a specific variable name.
     */
    private void assertVariableStoredCorrectly(String variableName, String message) {
        WorkflowRepository workflowRepository = mock(WorkflowRepository.class);
        ChatSessionRepository chatSessionRepository = mock(ChatSessionRepository.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        PlaceholderService placeholderService = new PlaceholderService();
        List<NodeProcessor> processors = List.of(new InputNodeProcessor(), new MessageNodeProcessor());

        WorkflowExecutionServiceImpl service = new WorkflowExecutionServiceImpl(
                workflowRepository, chatSessionRepository, processors, placeholderService, messagingTemplate);

        String sessionId = "test-session-" + variableName;

        ChatSession session = new ChatSession();
        session.setSessionId(sessionId);
        session.setWorkflowId(1L);
        session.setCurrentNodeId("input-node-1");
        session.setCurrentType("state");
        session.setCurrentNodeType("input");
        session.setStatus("active");

        Map<String, Object> context = new HashMap<>();
        context.put("_inputVariableName", variableName);
        session.setContext(context);

        when(chatSessionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(session));
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(inv -> inv.getArgument(0));

        Workflow workflow = new Workflow();
        workflow.setId(1L);
        workflow.setName("Test Workflow");

        Map<String, Object> inputNode = new HashMap<>();
        inputNode.put("id", "input-node-1");
        inputNode.put("name", "Enter " + variableName);
        inputNode.put("type", "state");
        inputNode.put("config", Map.of("nodeType", "input", "variableName", variableName));

        Map<String, Object> nextNode = new HashMap<>();
        nextNode.put("id", "msg-node-2");
        nextNode.put("name", "Thank you");
        nextNode.put("type", "state");
        nextNode.put("config", new HashMap<>());

        Map<String, Object> transition = new HashMap<>();
        transition.put("sourceNodeId", "input-node-1");
        transition.put("targetNodeId", "msg-node-2");

        Map<String, Object> workflowJson = new HashMap<>();
        workflowJson.put("nodes", List.of(inputNode, nextNode));
        workflowJson.put("transitions", List.of(transition));
        workflow.setWorkflowJson(workflowJson);

        when(workflowRepository.findById(1L)).thenReturn(Optional.of(workflow));

        // Act
        service.handleUserInput(sessionId, message);

        // Assert
        Map<String, Object> resultContext = session.getContext();

        assertThat(resultContext.get(variableName))
                .as("Reply should be stored under key '%s'", variableName)
                .isEqualTo(message);

        assertThat(resultContext.get("_inputVariableName"))
                .as("Temporary key '_inputVariableName' should be cleaned up")
                .isNull();

        assertThat(resultContext.get("mobile_no"))
                .as("Reply should NOT be stored under 'mobile_no' when variableName='%s'", variableName)
                .isNotEqualTo(message);
    }

    /**
     * Generates arbitrary non-empty variable names excluding "mobile_no".
     * These represent different input node config.variableName values.
     */
    @Provide
    Arbitrary<String> variableNames() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(30)
                .map(s -> s.toLowerCase())
                .filter(s -> !s.isEmpty() && !"mobile_no".equals(s) && !s.isBlank());
    }

    /**
     * Generates arbitrary non-empty user reply strings.
     * Uses alpha-only strings so jqwik cannot shrink to blank/whitespace values.
     */
    @Provide
    Arbitrary<String> userReplies() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(100);
    }
}

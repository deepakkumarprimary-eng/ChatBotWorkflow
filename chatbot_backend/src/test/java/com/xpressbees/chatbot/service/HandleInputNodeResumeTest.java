package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.entity.ChatSession;
import com.xpressbees.chatbot.entity.Workflow;
import com.xpressbees.chatbot.processor.InputNodeProcessor;
import com.xpressbees.chatbot.processor.MessageNodeProcessor;
import com.xpressbees.chatbot.processor.NodeProcessor;
import com.xpressbees.chatbot.repository.ChatSessionRepository;
import com.xpressbees.chatbot.repository.WorkflowRepository;
import com.xpressbees.chatbot.dto.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for handleInputNodeResume() variable name usage.
 * Tests are exercised via the public handleUserInput(sessionId, message) method
 * since handleInputNodeResume is private.
 *
 * Validates: Requirements 2.1, 2.4, 3.1
 */
class HandleInputNodeResumeTest {

    private WorkflowRepository workflowRepository;
    private ChatSessionRepository chatSessionRepository;
    private SimpMessagingTemplate messagingTemplate;
    private InputValidationService inputValidationService;
    private WorkflowExecutionServiceImpl service;

    @BeforeEach
    void setUp() {
        workflowRepository = mock(WorkflowRepository.class);
        chatSessionRepository = mock(ChatSessionRepository.class);
        messagingTemplate = mock(SimpMessagingTemplate.class);
        inputValidationService = mock(InputValidationService.class);
        PlaceholderService placeholderService = new PlaceholderService();
        List<NodeProcessor> processors = List.of(new InputNodeProcessor(), new MessageNodeProcessor());

        when(inputValidationService.validate(any(), any())).thenReturn(new ValidationResult(true, null));

        service = new WorkflowExecutionServiceImpl(
                workflowRepository, chatSessionRepository, processors, placeholderService, messagingTemplate, inputValidationService);
    }

    /**
     * Test 1: When context has "_inputVariableName" = "email" and user replies "a@b.com"
     * → context has "email" = "a@b.com" and no "_inputVariableName" key
     */
    @Test
    @DisplayName("Reply stored under configured variable name 'email' from _inputVariableName context key")
    void replyStoredUnderEmailVariableName() {
        String sessionId = "session-email-test";
        String variableName = "email";
        String userReply = "a@b.com";

        ChatSession session = createInputSession(sessionId, "input-node-1", variableName);
        setupMocks(sessionId, session, "input-node-1", variableName);

        service.handleUserInput(sessionId, userReply);

        Map<String, Object> resultContext = session.getContext();
        assertThat(resultContext.get("email"))
                .as("Reply should be stored under key 'email'")
                .isEqualTo("a@b.com");
        assertThat(resultContext).doesNotContainKey("_inputVariableName");
    }

    /**
     * Test 2: When context has "_inputVariableName" = "mobile_no" and user replies "1234"
     * → context has "mobile_no" = "1234" (preserves original behavior for this case)
     */
    @Test
    @DisplayName("Reply stored under 'mobile_no' when _inputVariableName is 'mobile_no' (preserves original behavior)")
    void replyStoredUnderMobileNoVariableName() {
        String sessionId = "session-mobile-test";
        String variableName = "mobile_no";
        String userReply = "1234";

        ChatSession session = createInputSession(sessionId, "input-node-1", variableName);
        setupMocks(sessionId, session, "input-node-1", variableName);

        service.handleUserInput(sessionId, userReply);

        Map<String, Object> resultContext = session.getContext();
        assertThat(resultContext.get("mobile_no"))
                .as("Reply should be stored under key 'mobile_no'")
                .isEqualTo("1234");
        assertThat(resultContext).doesNotContainKey("_inputVariableName");
    }

    /**
     * Test 3: When context has no "_inputVariableName" key
     * → falls back to node id (session.getCurrentNodeId())
     */
    @Test
    @DisplayName("Falls back to current node id when _inputVariableName is absent from context")
    void fallsBackToNodeIdWhenNoInputVariableName() {
        String sessionId = "session-fallback-test";
        String nodeId = "input-node-xyz";
        String userReply = "some-value";

        // Create session WITHOUT _inputVariableName in context
        ChatSession session = new ChatSession();
        session.setSessionId(sessionId);
        session.setWorkflowId(1L);
        session.setCurrentNodeId(nodeId);
        session.setCurrentType("state");
        session.setCurrentNodeType("input");
        session.setStatus("active");
        session.setContext(new HashMap<>());  // No _inputVariableName

        when(chatSessionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(session));
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(inv -> inv.getArgument(0));

        Workflow workflow = createWorkflowWithInputAndNextNode(nodeId, null);
        when(workflowRepository.findById(1L)).thenReturn(Optional.of(workflow));

        service.handleUserInput(sessionId, userReply);

        Map<String, Object> resultContext = session.getContext();
        assertThat(resultContext.get(nodeId))
                .as("Reply should be stored under node id '%s' as fallback", nodeId)
                .isEqualTo("some-value");
    }

    /**
     * Test 4: After storing reply, "_inputVariableName" is removed from context
     */
    @Test
    @DisplayName("_inputVariableName is removed from context after storing reply")
    void inputVariableNameRemovedAfterStoringReply() {
        String sessionId = "session-cleanup-test";
        String variableName = "user_name";
        String userReply = "John Doe";

        ChatSession session = createInputSession(sessionId, "input-node-1", variableName);
        setupMocks(sessionId, session, "input-node-1", variableName);

        // Verify _inputVariableName exists before call
        assertThat(session.getContext()).containsKey("_inputVariableName");

        service.handleUserInput(sessionId, userReply);

        // Verify it's removed after
        assertThat(session.getContext())
                .as("_inputVariableName should be removed from context after reply is stored")
                .doesNotContainKey("_inputVariableName");
        assertThat(session.getContext().get("user_name")).isEqualTo("John Doe");
    }

    /**
     * Test 5: Other context keys are not modified during resume
     */
    @Test
    @DisplayName("Other context keys are not modified during input node resume")
    void otherContextKeysNotModifiedDuringResume() {
        String sessionId = "session-preserve-test";
        String variableName = "city";
        String userReply = "Mumbai";

        ChatSession session = new ChatSession();
        session.setSessionId(sessionId);
        session.setWorkflowId(1L);
        session.setCurrentNodeId("input-node-1");
        session.setCurrentType("state");
        session.setCurrentNodeType("input");
        session.setStatus("active");

        // Pre-populate context with existing keys
        Map<String, Object> context = new HashMap<>();
        context.put("_inputVariableName", variableName);
        context.put("mobile_no", "9876543210");
        context.put("name", "Alice");
        context.put("order_id", "ORD-100");
        session.setContext(context);

        when(chatSessionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(session));
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(inv -> inv.getArgument(0));

        Workflow workflow = createWorkflowWithInputAndNextNode("input-node-1", variableName);
        when(workflowRepository.findById(1L)).thenReturn(Optional.of(workflow));

        service.handleUserInput(sessionId, userReply);

        Map<String, Object> resultContext = session.getContext();

        // New key stored correctly
        assertThat(resultContext.get("city")).isEqualTo("Mumbai");

        // Existing keys are untouched
        assertThat(resultContext.get("mobile_no"))
                .as("Existing 'mobile_no' key should not be modified")
                .isEqualTo("9876543210");
        assertThat(resultContext.get("name"))
                .as("Existing 'name' key should not be modified")
                .isEqualTo("Alice");
        assertThat(resultContext.get("order_id"))
                .as("Existing 'order_id' key should not be modified")
                .isEqualTo("ORD-100");

        // Temporary key removed
        assertThat(resultContext).doesNotContainKey("_inputVariableName");
    }

    // ===== Helper methods =====

    /**
     * Creates a ChatSession configured as awaiting input with the given _inputVariableName in context.
     */
    private ChatSession createInputSession(String sessionId, String nodeId, String variableName) {
        ChatSession session = new ChatSession();
        session.setSessionId(sessionId);
        session.setWorkflowId(1L);
        session.setCurrentNodeId(nodeId);
        session.setCurrentType("state");
        session.setCurrentNodeType("input");
        session.setStatus("active");

        Map<String, Object> context = new HashMap<>();
        context.put("_inputVariableName", variableName);
        session.setContext(context);

        return session;
    }

    /**
     * Sets up repository mocks for a standard input resume test scenario.
     */
    private void setupMocks(String sessionId, ChatSession session, String inputNodeId, String variableName) {
        when(chatSessionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(session));
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(inv -> inv.getArgument(0));

        Workflow workflow = createWorkflowWithInputAndNextNode(inputNodeId, variableName);
        when(workflowRepository.findById(1L)).thenReturn(Optional.of(workflow));
    }

    /**
     * Creates a Workflow with one input node and one message node connected by a transition.
     * This ensures handleInputNodeResume can resolve a next node without hitting the "no next node" path.
     */
    private Workflow createWorkflowWithInputAndNextNode(String inputNodeId, String variableName) {
        Workflow workflow = new Workflow();
        workflow.setId(1L);
        workflow.setName("Test Workflow");

        Map<String, Object> inputNode = new HashMap<>();
        inputNode.put("id", inputNodeId);
        inputNode.put("name", "Enter value");
        inputNode.put("type", "state");
        Map<String, Object> inputConfig = new HashMap<>();
        inputConfig.put("nodeType", "input");
        if (variableName != null) {
            inputConfig.put("variableName", variableName);
        }
        inputNode.put("config", inputConfig);

        Map<String, Object> nextNode = new HashMap<>();
        nextNode.put("id", "msg-node-end");
        nextNode.put("name", "Thank you");
        nextNode.put("type", "state");
        nextNode.put("config", new HashMap<>());

        Map<String, Object> transition = new HashMap<>();
        transition.put("sourceNodeId", inputNodeId);
        transition.put("targetNodeId", "msg-node-end");

        Map<String, Object> workflowJson = new HashMap<>();
        workflowJson.put("nodes", List.of(inputNode, nextNode));
        workflowJson.put("transitions", List.of(transition));
        workflow.setWorkflowJson(workflowJson);

        return workflow;
    }
}

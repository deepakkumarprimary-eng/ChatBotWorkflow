package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.dto.NodeProcessingResult;
import com.xpressbees.chatbot.entity.ChatSession;
import com.xpressbees.chatbot.entity.Workflow;
import com.xpressbees.chatbot.processor.InputNodeProcessor;
import com.xpressbees.chatbot.processor.MessageNodeProcessor;
import com.xpressbees.chatbot.processor.NodeProcessor;
import com.xpressbees.chatbot.repository.ChatSessionRepository;
import com.xpressbees.chatbot.repository.WorkflowRepository;
import net.jqwik.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Property 2: Preservation — Mobile_no Variable Name and Pause/Resume Flow Unchanged
 *
 * These tests observe and lock the CURRENT behavior on UNFIXED code:
 * - Input node resumes always store under "mobile_no" (the hardcoded key)
 * - InputNodeProcessor.process() sets currentNodeType="input", currentNodeId=node.id, returns PAUSE
 * - After resume: workflow is loaded, next node is resolved, session is persisted
 * - API node _displayVariable and _buttonOptions handling is not affected by input node logic
 *
 * Validates: Requirements 3.1, 3.2, 3.3, 3.4
 */
class InputNodePreservationPropertyTest {

    // ===========================================================================================
    // Property: For all generated user reply strings, when _inputVariableName = "mobile_no" is
    // in context, handleInputNodeResume() stores reply under "mobile_no" (same as current behavior)
    // ===========================================================================================

    @Property(tries = 100)
    @Tag("Preservation")
    void inputNodeResumeAlwaysStoresUnderMobileNo(@ForAll("nonBlankReplies") String userReply) {
        // **Validates: Requirements 3.1**
        // On UNFIXED code, handleInputNodeResume() hardcodes context.put("mobile_no", message)
        // This test confirms that behavior is preserved for ALL user replies.

        WorkflowRepository workflowRepo = mock(WorkflowRepository.class);
        ChatSessionRepository chatSessionRepo = mock(ChatSessionRepository.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        PlaceholderService placeholderService = new PlaceholderService();
        List<NodeProcessor> processors = List.of(new InputNodeProcessor(), new MessageNodeProcessor());

        WorkflowExecutionServiceImpl service = new WorkflowExecutionServiceImpl(
                workflowRepo, chatSessionRepo, processors, placeholderService, messagingTemplate, null);

        // Setup session as if paused on an input node
        String sessionId = "session-" + UUID.randomUUID();
        ChatSession session = new ChatSession();
        session.setSessionId(sessionId);
        session.setWorkflowId(1L);
        session.setCurrentNodeId("input-node-1");
        session.setCurrentType("state");
        session.setCurrentNodeType("input");
        session.setStatus("active");

        Map<String, Object> context = new HashMap<>();
        context.put("_inputVariableName", "mobile_no");
        context.put("existingKey", "existingValue");
        session.setContext(context);

        when(chatSessionRepo.findBySessionId(sessionId)).thenReturn(Optional.of(session));

        // Create a workflow with one input node and a terminal next message node
        Map<String, Object> inputNode = Map.of(
                "id", "input-node-1",
                "name", "Enter mobile number",
                "type", "state",
                "config", Map.of("nodeType", "input", "variableName", "mobile_no")
        );
        Map<String, Object> nextNode = Map.of(
                "id", "msg-node-2",
                "name", "Thank you",
                "type", "state",
                "config", Map.of("nodeType", "message")
        );
        Map<String, Object> transition = Map.of(
                "sourceNodeId", "input-node-1",
                "targetNodeId", "msg-node-2"
        );

        Workflow workflow = new Workflow();
        workflow.setId(1L);
        workflow.setWorkflowJson(Map.of(
                "nodes", List.of(inputNode, nextNode),
                "transitions", List.of(transition)
        ));

        when(workflowRepo.findById(1L)).thenReturn(Optional.of(workflow));
        when(chatSessionRepo.save(any(ChatSession.class))).thenAnswer(inv -> inv.getArgument(0));

        // Call the public handleUserInput method which delegates to handleInputNodeResume
        service.handleUserInput(sessionId, userReply);

        // Assert: On UNFIXED code, reply is ALWAYS stored under "mobile_no"
        assertThat(session.getContext().get("mobile_no")).isEqualTo(userReply);
    }

    // ===========================================================================================
    // Property: For all input nodes, InputNodeProcessor.process() sets currentNodeType to "input",
    // currentNodeId to node id, and returns PAUSE action
    // ===========================================================================================

    @Property(tries = 100)
    @Tag("Preservation")
    void inputNodeProcessorSetsPauseStateCorrectly(@ForAll("inputNodeIds") String nodeId) {
        // **Validates: Requirements 3.2**
        // Verifies pause mechanics are correct for ALL generated node IDs.

        InputNodeProcessor processor = new InputNodeProcessor();
        PlaceholderService placeholderService = new PlaceholderService();

        ChatSession session = new ChatSession();
        session.setSessionId("test-session");
        session.setContext(new HashMap<>());
        session.setStatus("active");

        Map<String, Object> inputNode = new HashMap<>();
        inputNode.put("id", nodeId);
        inputNode.put("name", "Please provide input");
        inputNode.put("type", "state");
        inputNode.put("config", Map.of("nodeType", "input", "variableName", "mobile_no"));

        NodeProcessingResult result = processor.process(inputNode, session, placeholderService);

        // Assert PAUSE mechanics
        assertThat(result.getAction()).isEqualTo(NodeProcessingResult.Action.PAUSE);
        assertThat(session.getCurrentNodeType()).isEqualTo("input");
        assertThat(session.getCurrentNodeId()).isEqualTo(nodeId);
        assertThat(session.getCurrentType()).isEqualTo("state");

        // Assert response is created with session ID and prompt
        assertThat(result.getResponse()).isNotNull();
        assertThat(result.getResponse().getSessionId()).isEqualTo("test-session");
    }

    // ===========================================================================================
    // Property: For all input node resumes, the method loads the workflow, resolves next node,
    // and persists session (verify mock interactions)
    // ===========================================================================================

    @Property(tries = 50)
    @Tag("Preservation")
    void inputNodeResumeLoadsWorkflowResolvesNextAndPersists(@ForAll("nonBlankReplies") String userReply) {
        // **Validates: Requirements 3.3**
        // Verifies that after resume: workflow is loaded, next node resolved, session persisted.

        WorkflowRepository workflowRepo = mock(WorkflowRepository.class);
        ChatSessionRepository chatSessionRepo = mock(ChatSessionRepository.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        PlaceholderService placeholderService = new PlaceholderService();
        List<NodeProcessor> processors = List.of(new InputNodeProcessor(), new MessageNodeProcessor());

        WorkflowExecutionServiceImpl service = new WorkflowExecutionServiceImpl(
                workflowRepo, chatSessionRepo, processors, placeholderService, messagingTemplate, null);

        String sessionId = "session-resume-" + UUID.randomUUID();
        ChatSession session = new ChatSession();
        session.setSessionId(sessionId);
        session.setWorkflowId(5L);
        session.setCurrentNodeId("node-input-42");
        session.setCurrentType("state");
        session.setCurrentNodeType("input");
        session.setStatus("active");
        session.setContext(new HashMap<>());

        when(chatSessionRepo.findBySessionId(sessionId)).thenReturn(Optional.of(session));

        // Workflow with input node -> message node (terminal)
        Map<String, Object> inputNode = Map.of(
                "id", "node-input-42",
                "name", "Input prompt",
                "type", "state",
                "config", Map.of("nodeType", "input")
        );
        Map<String, Object> msgNode = Map.of(
                "id", "node-msg-43",
                "name", "Done!",
                "type", "state",
                "config", Map.of("nodeType", "message")
        );
        Map<String, Object> transition = Map.of(
                "sourceNodeId", "node-input-42",
                "targetNodeId", "node-msg-43"
        );

        Workflow workflow = new Workflow();
        workflow.setId(5L);
        workflow.setWorkflowJson(Map.of(
                "nodes", List.of(inputNode, msgNode),
                "transitions", List.of(transition)
        ));

        when(workflowRepo.findById(5L)).thenReturn(Optional.of(workflow));
        when(chatSessionRepo.save(any(ChatSession.class))).thenAnswer(inv -> inv.getArgument(0));

        // Execute resume
        service.handleUserInput(sessionId, userReply);

        // Verify: workflow was loaded
        verify(workflowRepo).findById(5L);

        // Verify: session was persisted (at least once for the resume save, possibly more for completion)
        verify(chatSessionRepo, atLeastOnce()).save(any(ChatSession.class));

        // Verify: messaging template was used (next node processed and response sent)
        verify(messagingTemplate, atLeastOnce()).convertAndSend(anyString(), any(Object.class));
    }

    // ===========================================================================================
    // Deterministic: Verify handleApiNodeResume() still reads _displayVariable and _buttonOptions
    // from context without interference from input node logic
    // ===========================================================================================

    @Example
    @Tag("Preservation")
    void apiNodeResumeReadsDisplayVariableWithoutInputNodeInterference() {
        // **Validates: Requirements 3.4**
        // Verifies API node _displayVariable handling is independent of input node logic.

        WorkflowRepository workflowRepo = mock(WorkflowRepository.class);
        ChatSessionRepository chatSessionRepo = mock(ChatSessionRepository.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        PlaceholderService placeholderService = new PlaceholderService();
        List<NodeProcessor> processors = List.of(new InputNodeProcessor(), new MessageNodeProcessor());

        WorkflowExecutionServiceImpl service = new WorkflowExecutionServiceImpl(
                workflowRepo, chatSessionRepo, processors, placeholderService, messagingTemplate, null);

        String sessionId = "api-session-1";
        ChatSession session = new ChatSession();
        session.setSessionId(sessionId);
        session.setWorkflowId(2L);
        session.setCurrentNodeId("api-node-1");
        session.setCurrentType("state");
        session.setCurrentNodeType("api");
        session.setStatus("active");

        Map<String, Object> context = new HashMap<>();
        context.put("_displayVariable", "cities");
        context.put("cities", "Mumbai\nDelhi\nBangalore");
        session.setContext(context);

        when(chatSessionRepo.findBySessionId(sessionId)).thenReturn(Optional.of(session));

        // Workflow with api node -> message node
        Map<String, Object> apiNode = Map.of(
                "id", "api-node-1",
                "name", "Select city",
                "type", "state",
                "config", Map.of("nodeType", "api")
        );
        Map<String, Object> msgNode = Map.of(
                "id", "msg-node-2",
                "name", "You selected {{cities}}",
                "type", "state",
                "config", Map.of("nodeType", "message")
        );
        Map<String, Object> transition = Map.of(
                "sourceNodeId", "api-node-1",
                "targetNodeId", "msg-node-2"
        );

        Workflow workflow = new Workflow();
        workflow.setId(2L);
        workflow.setWorkflowJson(Map.of(
                "nodes", List.of(apiNode, msgNode),
                "transitions", List.of(transition)
        ));

        when(workflowRepo.findById(2L)).thenReturn(Optional.of(workflow));
        when(chatSessionRepo.save(any(ChatSession.class))).thenAnswer(inv -> inv.getArgument(0));

        // User selects "Mumbai" (a valid option)
        service.handleUserInput(sessionId, "Mumbai");

        // Assert: _displayVariable was read and removed, city selection stored
        assertThat(session.getContext().get("_displayVariable")).isNull();
        assertThat(session.getContext().get("cities")).isEqualTo("Mumbai");
    }

    @Example
    @Tag("Preservation")
    void apiNodeResumeReadsButtonOptionsWithoutInputNodeInterference() {
        // **Validates: Requirements 3.4**
        // Verifies API node _buttonOptions handling is independent of input node logic.

        WorkflowRepository workflowRepo = mock(WorkflowRepository.class);
        ChatSessionRepository chatSessionRepo = mock(ChatSessionRepository.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        PlaceholderService placeholderService = new PlaceholderService();
        List<NodeProcessor> processors = List.of(new InputNodeProcessor(), new MessageNodeProcessor());

        WorkflowExecutionServiceImpl service = new WorkflowExecutionServiceImpl(
                workflowRepo, chatSessionRepo, processors, placeholderService, messagingTemplate, null);

        String sessionId = "api-session-btn";
        ChatSession session = new ChatSession();
        session.setSessionId(sessionId);
        session.setWorkflowId(3L);
        session.setCurrentNodeId("api-btn-node");
        session.setCurrentType("state");
        session.setCurrentNodeType("api");
        session.setStatus("active");

        Map<String, Object> context = new HashMap<>();
        context.put("_buttonOptions", "Option A\nOption B");
        session.setContext(context);

        when(chatSessionRepo.findBySessionId(sessionId)).thenReturn(Optional.of(session));

        // Workflow with api node and two target nodes (button options)
        Map<String, Object> apiNode = Map.of(
                "id", "api-btn-node",
                "name", "Choose option",
                "type", "state",
                "config", Map.of("nodeType", "api")
        );
        Map<String, Object> targetNodeA = Map.of(
                "id", "target-a",
                "name", "Option A",
                "type", "state",
                "config", Map.of("nodeType", "message")
        );
        Map<String, Object> targetNodeB = Map.of(
                "id", "target-b",
                "name", "Option B",
                "type", "state",
                "config", Map.of("nodeType", "message")
        );
        Map<String, Object> transitionA = Map.of(
                "sourceNodeId", "api-btn-node",
                "targetNodeId", "target-a"
        );
        Map<String, Object> transitionB = Map.of(
                "sourceNodeId", "api-btn-node",
                "targetNodeId", "target-b"
        );

        Workflow workflow = new Workflow();
        workflow.setId(3L);
        workflow.setWorkflowJson(Map.of(
                "nodes", List.of(apiNode, targetNodeA, targetNodeB),
                "transitions", List.of(transitionA, transitionB)
        ));

        when(workflowRepo.findById(3L)).thenReturn(Optional.of(workflow));
        when(chatSessionRepo.save(any(ChatSession.class))).thenAnswer(inv -> inv.getArgument(0));

        // User selects "Option A" (a valid button option matching a target node name)
        service.handleUserInput(sessionId, "Option A");

        // Assert: _buttonOptions was read and removed
        assertThat(session.getContext().get("_buttonOptions")).isNull();
    }

    // ===========================================================================================
    // Deterministic: Verify existing context keys (other than storage target) are not modified
    // during input node resume
    // ===========================================================================================

    @Example
    @Tag("Preservation")
    void existingContextKeysAreNotModifiedDuringInputNodeResume() {
        // **Validates: Requirements 3.3, 3.4**
        // Verifies that input node resume does not modify pre-existing context keys
        // (other than the storage target "mobile_no" on unfixed code).

        WorkflowRepository workflowRepo = mock(WorkflowRepository.class);
        ChatSessionRepository chatSessionRepo = mock(ChatSessionRepository.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        PlaceholderService placeholderService = new PlaceholderService();
        List<NodeProcessor> processors = List.of(new InputNodeProcessor(), new MessageNodeProcessor());

        WorkflowExecutionServiceImpl service = new WorkflowExecutionServiceImpl(
                workflowRepo, chatSessionRepo, processors, placeholderService, messagingTemplate, null);

        String sessionId = "context-preserve-session";
        ChatSession session = new ChatSession();
        session.setSessionId(sessionId);
        session.setWorkflowId(1L);
        session.setCurrentNodeId("input-node-1");
        session.setCurrentType("state");
        session.setCurrentNodeType("input");
        session.setStatus("active");

        Map<String, Object> context = new HashMap<>();
        context.put("_inputVariableName", "mobile_no");
        context.put("name", "John Doe");
        context.put("email", "john@example.com");
        context.put("order_count", 5);
        session.setContext(context);

        when(chatSessionRepo.findBySessionId(sessionId)).thenReturn(Optional.of(session));

        Map<String, Object> inputNode = Map.of(
                "id", "input-node-1",
                "name", "Enter mobile",
                "type", "state",
                "config", Map.of("nodeType", "input", "variableName", "mobile_no")
        );
        Map<String, Object> msgNode = Map.of(
                "id", "msg-node-2",
                "name", "Thanks",
                "type", "state",
                "config", Map.of("nodeType", "message")
        );
        Map<String, Object> transition = Map.of(
                "sourceNodeId", "input-node-1",
                "targetNodeId", "msg-node-2"
        );

        Workflow workflow = new Workflow();
        workflow.setId(1L);
        workflow.setWorkflowJson(Map.of(
                "nodes", List.of(inputNode, msgNode),
                "transitions", List.of(transition)
        ));

        when(workflowRepo.findById(1L)).thenReturn(Optional.of(workflow));
        when(chatSessionRepo.save(any(ChatSession.class))).thenAnswer(inv -> inv.getArgument(0));

        // Resume with user reply
        service.handleUserInput(sessionId, "9876543210");

        // Assert: pre-existing keys are NOT modified
        assertThat(session.getContext().get("name")).isEqualTo("John Doe");
        assertThat(session.getContext().get("email")).isEqualTo("john@example.com");
        assertThat(session.getContext().get("order_count")).isEqualTo(5);

        // On unfixed code, "mobile_no" is stored (this is the current behavior we preserve)
        assertThat(session.getContext().get("mobile_no")).isEqualTo("9876543210");
    }

    // ===========================================================================================
    // Generators
    // ===========================================================================================

    @Provide
    Arbitrary<String> userReplies() {
        return Arbitraries.strings()
                .ofMinLength(1)
                .ofMaxLength(50)
                .alpha()
                .numeric()
                .withChars('_', '-', '@', '.', ' ');
    }

    @Provide
    Arbitrary<String> nonBlankReplies() {
        // Generates strings that are guaranteed non-blank (at least one non-whitespace char)
        // This is needed because handleUserInput() rejects blank messages early
        return Arbitraries.strings()
                .ofMinLength(1)
                .ofMaxLength(50)
                .alpha()
                .numeric()
                .withChars('_', '-', '@', '.')
                .filter(s -> !s.trim().isEmpty());
    }

    @Provide
    Arbitrary<String> inputNodeIds() {
        return Arbitraries.strings()
                .ofMinLength(1)
                .ofMaxLength(30)
                .alpha()
                .numeric()
                .withChars('-', '_');
    }
}

package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.dto.ChatResponse;
import com.xpressbees.chatbot.entity.ChatSession;
import com.xpressbees.chatbot.entity.Workflow;
import com.xpressbees.chatbot.processor.InputNodeProcessor;
import com.xpressbees.chatbot.processor.MessageNodeProcessor;
import com.xpressbees.chatbot.processor.NodeProcessor;
import com.xpressbees.chatbot.repository.ChatSessionRepository;
import com.xpressbees.chatbot.repository.WorkflowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration test verifying a multi-input-node workflow correctly stores
 * each user reply under its configured variableName, preserving all values
 * across sequential input nodes.
 *
 * Validates: Requirements 2.1, 2.5, 3.3
 */
class MultiInputNodeIntegrationTest {

    private static final String SESSION_ID = "integration-session-001";
    private static final Long WORKFLOW_ID = 42L;

    private WorkflowRepository workflowRepository;
    private ChatSessionRepository chatSessionRepository;
    private SimpMessagingTemplate messagingTemplate;
    private WorkflowExecutionServiceImpl service;
    private ChatSession session;

    @BeforeEach
    void setUp() {
        workflowRepository = mock(WorkflowRepository.class);
        chatSessionRepository = mock(ChatSessionRepository.class);
        messagingTemplate = mock(SimpMessagingTemplate.class);

        PlaceholderService placeholderService = new PlaceholderService();
        List<NodeProcessor> processors = List.of(new InputNodeProcessor(), new MessageNodeProcessor());

        service = new WorkflowExecutionServiceImpl(
                workflowRepository, chatSessionRepository, processors, placeholderService, messagingTemplate);

        // Create session — starts without a workflow ID
        session = new ChatSession();
        session.setSessionId(SESSION_ID);
        session.setStatus("active");
        session.setContext(new HashMap<>());

        // Mock findBySessionId to always return the same session object (state accumulates)
        when(chatSessionRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.of(session));

        // Mock save to just return the argument (passthrough)
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Build and mock the workflow
        Workflow workflow = buildMultiInputWorkflow();
        when(workflowRepository.findById(WORKFLOW_ID)).thenReturn(Optional.of(workflow));
    }

    @Test
    void multiInputWorkflow_preservesAllVariableNamesInContext() {
        // Step 1: Start workflow — first input node (mobile_no) pauses
        service.startWorkflow(SESSION_ID, WORKFLOW_ID);

        // Verify first input node sent its prompt
        assertInputNodePaused("input_mobile");

        // Step 2: Reply with mobile number — stores mobile_no, second input (email) pauses
        service.handleUserInput(SESSION_ID, "9876543210");

        assertInputNodePaused("input_email");

        // Step 3: Reply with email — stores email, third input (order_id) pauses
        service.handleUserInput(SESSION_ID, "user@test.com");

        assertInputNodePaused("input_order");

        // Step 4: Reply with order ID — stores order_id, message node processes and workflow completes
        service.handleUserInput(SESSION_ID, "ORD-12345");

        // Assert: Final context contains all three values preserved correctly
        Map<String, Object> context = session.getContext();
        assertEquals("9876543210", context.get("mobile_no"),
                "mobile_no should be preserved after subsequent input nodes");
        assertEquals("user@test.com", context.get("email"),
                "email should be preserved after subsequent input nodes");
        assertEquals("ORD-12345", context.get("order_id"),
                "order_id should be stored from the third input node reply");
    }

    @Test
    void multiInputWorkflow_downstreamMessageNodeResolvesEmailPlaceholder() {
        // Execute full flow
        service.startWorkflow(SESSION_ID, WORKFLOW_ID);
        service.handleUserInput(SESSION_ID, "9876543210");
        service.handleUserInput(SESSION_ID, "user@test.com");
        service.handleUserInput(SESSION_ID, "ORD-12345");

        // Capture all messages sent via the messaging template
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate, atLeast(1)).convertAndSend(
                eq("/topic/chat/" + SESSION_ID), captor.capture());

        // Find the message node response that resolves {{email}}
        List<Object> allResponses = captor.getAllValues();
        boolean foundResolvedEmail = allResponses.stream()
                .filter(r -> r instanceof ChatResponse)
                .map(r -> (ChatResponse) r)
                .anyMatch(r -> r.getResponse() != null && r.getResponse().contains("user@test.com"));

        assertTrue(foundResolvedEmail,
                "Downstream message node should resolve {{email}} to 'user@test.com'");
    }

    @Test
    void multiInputWorkflow_placeholderServiceResolvesOrderIdForApiUrl() {
        // Execute full flow to populate context
        service.startWorkflow(SESSION_ID, WORKFLOW_ID);
        service.handleUserInput(SESSION_ID, "9876543210");
        service.handleUserInput(SESSION_ID, "user@test.com");
        service.handleUserInput(SESSION_ID, "ORD-12345");

        // Verify PlaceholderService can resolve {{order_id}} in an API URL template
        PlaceholderService placeholderService = new PlaceholderService();
        String urlTemplate = "https://api.example.com/orders/{{order_id}}/status";
        String resolvedUrl = placeholderService.resolve(urlTemplate, session.getContext());

        assertEquals("https://api.example.com/orders/ORD-12345/status", resolvedUrl,
                "PlaceholderService should resolve {{order_id}} in URL from session context");
    }

    /**
     * Asserts that the session is currently paused at the specified input node.
     */
    private void assertInputNodePaused(String expectedNodeId) {
        assertEquals(expectedNodeId, session.getCurrentNodeId(),
                "Session should be paused at node: " + expectedNodeId);
        assertEquals("input", session.getCurrentNodeType(),
                "Current node type should be 'input'");
    }

    /**
     * Builds a workflow with three sequential input nodes followed by a message node:
     *
     * [input_mobile] --> [input_email] --> [input_order] --> [msg_confirm]
     *  variableName=      variableName=     variableName=    name="Your email: {{email}}"
     *  "mobile_no"        "email"           "order_id"
     */
    private Workflow buildMultiInputWorkflow() {
        // Node 1: Input node for mobile number
        Map<String, Object> inputMobileConfig = new HashMap<>();
        inputMobileConfig.put("nodeType", "input");
        inputMobileConfig.put("variableName", "mobile_no");

        Map<String, Object> inputMobileNode = new HashMap<>();
        inputMobileNode.put("id", "input_mobile");
        inputMobileNode.put("name", "Enter your mobile number");
        inputMobileNode.put("type", "state");
        inputMobileNode.put("config", inputMobileConfig);

        // Node 2: Input node for email
        Map<String, Object> inputEmailConfig = new HashMap<>();
        inputEmailConfig.put("nodeType", "input");
        inputEmailConfig.put("variableName", "email");

        Map<String, Object> inputEmailNode = new HashMap<>();
        inputEmailNode.put("id", "input_email");
        inputEmailNode.put("name", "Enter your email");
        inputEmailNode.put("type", "state");
        inputEmailNode.put("config", inputEmailConfig);

        // Node 3: Input node for order ID
        Map<String, Object> inputOrderConfig = new HashMap<>();
        inputOrderConfig.put("nodeType", "input");
        inputOrderConfig.put("variableName", "order_id");

        Map<String, Object> inputOrderNode = new HashMap<>();
        inputOrderNode.put("id", "input_order");
        inputOrderNode.put("name", "Enter your order ID");
        inputOrderNode.put("type", "state");
        inputOrderNode.put("config", inputOrderConfig);

        // Node 4: Message node confirming email (uses {{email}} placeholder)
        Map<String, Object> msgConfirmNode = new HashMap<>();
        msgConfirmNode.put("id", "msg_confirm");
        msgConfirmNode.put("name", "Your email: {{email}}");
        msgConfirmNode.put("type", "state");
        // No config with nodeType → handled by MessageNodeProcessor

        // Transitions: input_mobile → input_email → input_order → msg_confirm
        List<Map<String, Object>> transitions = new ArrayList<>();
        transitions.add(Map.of("sourceNodeId", "input_mobile", "targetNodeId", "input_email"));
        transitions.add(Map.of("sourceNodeId", "input_email", "targetNodeId", "input_order"));
        transitions.add(Map.of("sourceNodeId", "input_order", "targetNodeId", "msg_confirm"));

        // Nodes list
        List<Map<String, Object>> nodes = new ArrayList<>();
        nodes.add(inputMobileNode);
        nodes.add(inputEmailNode);
        nodes.add(inputOrderNode);
        nodes.add(msgConfirmNode);

        // Workflow JSON
        Map<String, Object> workflowJson = new HashMap<>();
        workflowJson.put("nodes", nodes);
        workflowJson.put("transitions", transitions);

        Workflow workflow = new Workflow();
        workflow.setId(WORKFLOW_ID);
        workflow.setName("Multi Input Test Workflow");
        workflow.setWorkflowJson(workflowJson);

        return workflow;
    }
}

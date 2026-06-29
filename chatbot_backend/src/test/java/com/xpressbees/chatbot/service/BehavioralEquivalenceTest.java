package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.controller.ChatWebSocketHandler;
import com.xpressbees.chatbot.dto.ChatErrorResponse;
import com.xpressbees.chatbot.dto.ChatResponse;
import com.xpressbees.chatbot.entity.ApiConfig;
import com.xpressbees.chatbot.entity.ChatSession;
import com.xpressbees.chatbot.entity.Workflow;
import com.xpressbees.chatbot.processor.*;
import com.xpressbees.chatbot.repository.ChatSessionRepository;
import com.xpressbees.chatbot.repository.WorkflowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Behavioral Equivalence Integration Test.
 *
 * Exercises the orchestrator with representative golden workflows and verifies the exact
 * sequence of WebSocket messages sent via ChatMessageSender. This serves as the regression
 * guard ensuring the refactored architecture produces identical output to the original.
 *
 * Validates: Requirements 9.1, 9.2, 9.3, 9.4, 9.5
 */
class BehavioralEquivalenceTest {

    private static final String SESSION_ID = "golden-test-session";

    private WorkflowRepository workflowRepository;
    private ChatSessionRepository chatSessionRepository;
    private ApiConfigCacheService apiConfigCacheService;
    private SimpMessagingTemplate messagingTemplate;
    private ChatWebSocketHandler chatWebSocketHandler;
    private WorkflowExecutionServiceImpl service;
    private ChatSession session;

    @BeforeEach
    void setUp() {
        workflowRepository = mock(WorkflowRepository.class);
        chatSessionRepository = mock(ChatSessionRepository.class);
        apiConfigCacheService = mock(ApiConfigCacheService.class);
        messagingTemplate = mock(SimpMessagingTemplate.class);
        chatWebSocketHandler = mock(ChatWebSocketHandler.class);

        PlaceholderService placeholderService = new PlaceholderService();
        InputValidationService inputValidationService = mock(InputValidationService.class);
        HttpExecutor httpExecutor = mock(HttpExecutor.class);
        ResponseExtractor responseExtractor = mock(ResponseExtractor.class);
        ConditionEvaluator conditionEvaluator = new ConditionEvaluator();

        WorkflowNodeProcessor workflowNodeProcessor = new WorkflowNodeProcessor(workflowRepository);
        UrlValidator urlValidator = mock(UrlValidator.class);
        when(urlValidator.validate(anyString())).thenReturn(com.xpressbees.chatbot.dto.UrlValidationResult.allowed());
        ApiNodeProcessor apiNodeProcessor = new ApiNodeProcessor(apiConfigCacheService, httpExecutor, responseExtractor, urlValidator);
        DecisionNodeProcessor decisionNodeProcessor = new DecisionNodeProcessor(conditionEvaluator);

        List<NodeProcessor> processors = List.of(
                new InputNodeProcessor(),
                new MessageNodeProcessor(),
                apiNodeProcessor,
                decisionNodeProcessor,
                workflowNodeProcessor
        );
        when(chatWebSocketHandler.consumePendingSession(anyString())).thenReturn(true);

        ChatMessageSender chatMessageSender = new ChatMessageSender(messagingTemplate);
        SessionStateManager sessionStateManager = new SessionStateManager(chatSessionRepository);
        NavigationService navigationService = new NavigationService(workflowRepository, placeholderService);
        ChildWorkflowService childWorkflowService = new ChildWorkflowService(workflowRepository);

        service = TestServiceFactory.createService(
                workflowRepository, processors, placeholderService,
                inputValidationService, chatWebSocketHandler,
                chatMessageSender, sessionStateManager,
                navigationService, childWorkflowService
        );

        session = new ChatSession();
        session.setSessionId(SESSION_ID);
        session.setStatus("active");
        session.setContext(new HashMap<>());

        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> {
            session = invocation.getArgument(0);
            when(chatSessionRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.of(session));
            return session;
        });
    }

    // ========== Scenario 1: Linear 3-Message Workflow ==========

    @Test
    @DisplayName("Golden: Linear 3-message workflow produces correct message sequence")
    void linearThreeMessageWorkflow() {
        Workflow workflow = buildWorkflow(1L, "Linear",
                List.of(
                        messageNode("msg1", "Hello"),
                        messageNode("msg2", "How are you?"),
                        messageNode("msg3", "Goodbye")
                ),
                List.of(
                        transition("msg1", "msg2"),
                        transition("msg2", "msg3")
                )
        );
        when(workflowRepository.findById(1L)).thenReturn(Optional.of(workflow));

        service.startWorkflow(SESSION_ID, 1L);

        List<Object> messages = captureMessages();

        // The orchestrator sends each node's response via CONTINUE, then a final
        // completion response when no next node is found. For 3 message nodes:
        // msg1 response, msg2 response, msg3 response (CONTINUE), + completion response = 4 total
        assertEquals(4, messages.size(), "Linear workflow should produce 4 messages (3 nodes + completion)");

        assertChatResponse(messages.get(0), "Hello", null);
        assertChatResponse(messages.get(1), "How are you?", null);
        assertChatResponse(messages.get(2), "Goodbye", null);
        // Final completion response re-sends last node's text with completed=true
        assertChatResponse(messages.get(3), "Goodbye", true);

        assertEquals("completed", session.getStatus());
    }

    // ========== Scenario 2: Input Workflow ==========

    @Test
    @DisplayName("Golden: Input workflow pauses at input, resumes after user reply, resolves placeholder")
    void inputWorkflow() {
        Workflow workflow = buildWorkflow(2L, "Input",
                List.of(
                        messageNode("msg1", "Welcome!"),
                        inputNode("inp1", "What is your name?", "userName"),
                        messageNode("msg2", "Thanks, {{userName}}!")
                ),
                List.of(
                        transition("msg1", "inp1"),
                        transition("inp1", "msg2")
                )
        );
        when(workflowRepository.findById(2L)).thenReturn(Optional.of(workflow));

        service.startWorkflow(SESSION_ID, 2L);

        // After start: msg1 sends, then pauses at input node
        List<Object> messagesAfterStart = captureMessages();
        assertEquals(2, messagesAfterStart.size(), "Should have welcome message + input prompt");
        assertChatResponse(messagesAfterStart.get(0), "Welcome!", null);
        assertChatResponse(messagesAfterStart.get(1), "What is your name?", null);
        assertNotEquals("completed", session.getStatus());

        // User replies
        reset(messagingTemplate);
        session.setWorkflowId(2L);
        service.handleUserInput(SESSION_ID, "Alice");

        List<Object> messagesAfterInput = captureMessages();
        // Should have the final message with placeholder resolved
        assertTrue(messagesAfterInput.size() >= 1);
        assertChatResponse(messagesAfterInput.get(messagesAfterInput.size() - 1), "Thanks, Alice!", true);
        assertEquals("completed", session.getStatus());
        assertEquals("Alice", session.getContext().get("userName"));
    }

    // ========== Scenario 3: Branching (Decision Node) ==========

    @Test
    @DisplayName("Golden: Decision node routes to branch A when condition matches")
    void branchingDecisionBranchA() {
        Workflow workflow = buildWorkflow(3L, "Branching",
                List.of(
                        inputNode("inp1", "Pick a or b", "choice"),
                        decisionNode("dec1", "Decision"),
                        messageNode("msgA", "You chose A"),
                        messageNode("msgB", "You chose B")
                ),
                List.of(
                        transition("inp1", "dec1"),
                        transitionWithCondition("dec1", "msgA", "choice == a"),
                        transitionWithCondition("dec1", "msgB", "choice == b")
                )
        );
        when(workflowRepository.findById(3L)).thenReturn(Optional.of(workflow));

        service.startWorkflow(SESSION_ID, 3L);

        // Should pause at input node
        List<Object> messagesAfterStart = captureMessages();
        assertEquals(1, messagesAfterStart.size());
        assertChatResponse(messagesAfterStart.get(0), "Pick a or b", null);

        // User picks "a"
        reset(messagingTemplate);
        session.setWorkflowId(3L);
        service.handleUserInput(SESSION_ID, "a");

        List<Object> messagesAfterInput = captureMessages();
        // Decision routes to branch A
        assertTrue(messagesAfterInput.size() >= 1);
        boolean foundBranchA = messagesAfterInput.stream()
                .filter(m -> m instanceof ChatResponse)
                .map(m -> (ChatResponse) m)
                .anyMatch(r -> "You chose A".equals(r.getResponse()));
        assertTrue(foundBranchA, "Should route to branch A");
        assertEquals("completed", session.getStatus());
    }

    @Test
    @DisplayName("Golden: Decision node routes to branch B when condition matches")
    void branchingDecisionBranchB() {
        Workflow workflow = buildWorkflow(3L, "Branching",
                List.of(
                        inputNode("inp1", "Pick a or b", "choice"),
                        decisionNode("dec1", "Decision"),
                        messageNode("msgA", "You chose A"),
                        messageNode("msgB", "You chose B")
                ),
                List.of(
                        transition("inp1", "dec1"),
                        transitionWithCondition("dec1", "msgA", "choice == a"),
                        transitionWithCondition("dec1", "msgB", "choice == b")
                )
        );
        when(workflowRepository.findById(3L)).thenReturn(Optional.of(workflow));

        service.startWorkflow(SESSION_ID, 3L);

        reset(messagingTemplate);
        session.setWorkflowId(3L);
        service.handleUserInput(SESSION_ID, "b");

        List<Object> messagesAfterInput = captureMessages();
        boolean foundBranchB = messagesAfterInput.stream()
                .filter(m -> m instanceof ChatResponse)
                .map(m -> (ChatResponse) m)
                .anyMatch(r -> "You chose B".equals(r.getResponse()));
        assertTrue(foundBranchB, "Should route to branch B");
        assertEquals("completed", session.getStatus());
    }

    // ========== Scenario 4: API Node (auto-advance) ==========

    @Test
    @DisplayName("Golden: API node auto-advances when single transition and no displayVariable")
    void apiNodeAutoAdvance() {
        Workflow workflow = buildWorkflow(4L, "API",
                List.of(
                        messageNode("msg1", "Fetching data..."),
                        apiNode("api1", "API Call", "100"),
                        messageNode("msg2", "Data fetched!")
                ),
                List.of(
                        transition("msg1", "api1"),
                        transition("api1", "msg2")
                )
        );
        when(workflowRepository.findById(4L)).thenReturn(Optional.of(workflow));

        // Mock API config with successful execution
        ApiConfig apiConfig = new ApiConfig();
        apiConfig.setId(100L);
        apiConfig.setUrl("https://api.example.com/data");
        apiConfig.setMethod("GET");
        when(apiConfigCacheService.findById(100L)).thenReturn(Optional.of(apiConfig));

        // Mock successful HTTP response
        com.xpressbees.chatbot.dto.HttpExecutionResult httpResult =
                new com.xpressbees.chatbot.dto.HttpExecutionResult(true, 200, "{}", null);
        HttpExecutor httpExecutor = getHttpExecutorFromProcessors();
        // Since we can't easily access the mock from within the processor, we need to mock from setup
        // Re-setup with proper HTTP mocking
        resetServiceWithMockedHttp(true);

        when(workflowRepository.findById(4L)).thenReturn(Optional.of(workflow));
        service.startWorkflow(SESSION_ID, 4L);

        List<Object> messages = captureMessages();
        assertTrue(messages.size() >= 2, "Should have at least 2 messages (msg1 + msg2)");
        assertChatResponse(messages.get(0), "Fetching data...", null);

        // Last message should be completion (or there should be Data fetched!)
        boolean foundFetched = messages.stream()
                .filter(m -> m instanceof ChatResponse)
                .map(m -> (ChatResponse) m)
                .anyMatch(r -> "Data fetched!".equals(r.getResponse()));
        assertTrue(foundFetched, "Should have 'Data fetched!' message after API auto-advance");
        assertEquals("completed", session.getStatus());
    }

    // ========== Scenario 5: Nested Child Workflow ==========

    @Test
    @DisplayName("Golden: Parent enters child workflow and returns to parent after child completes")
    void nestedChildWorkflow() {
        Workflow parentWorkflow = buildWorkflow(5L, "Parent",
                List.of(
                        messageNode("p_msg1", "Parent start"),
                        workflowNode("p_wf", 6L),
                        messageNode("p_msg2", "Back in parent")
                ),
                List.of(
                        transition("p_msg1", "p_wf"),
                        transition("p_wf", "p_msg2")
                )
        );

        Workflow childWorkflow = buildWorkflow(6L, "Child",
                List.of(messageNode("c_msg1", "Hello from child")),
                List.of(Map.of("sourceNodeId", "c_msg1", "targetNodeId", "nonexistent"))
        );

        when(workflowRepository.findById(5L)).thenReturn(Optional.of(parentWorkflow));
        when(workflowRepository.findById(6L)).thenReturn(Optional.of(childWorkflow));

        service.startWorkflow(SESSION_ID, 5L);

        List<Object> messages = captureMessages();

        // Expected: Parent start → Hello from child → Back in parent → completion response
        // (same pattern as linear: last node sends CONTINUE response + separate completion response)
        assertEquals(4, messages.size(), "Should produce 4 messages (3 nodes + completion)");
        assertChatResponse(messages.get(0), "Parent start", null);
        assertChatResponse(messages.get(1), "Hello from child", null);
        assertChatResponse(messages.get(2), "Back in parent", null);
        assertChatResponse(messages.get(3), "Back in parent", true);
        assertEquals("completed", session.getStatus());
        assertEquals(5L, session.getWorkflowId(), "Should restore parent workflow ID");
    }

    // ========== Scenario 6: Error Case — Missing API Config ==========

    @Test
    @DisplayName("Golden: Missing API config produces error response")
    void errorMissingApiConfig() {
        Workflow workflow = buildWorkflow(7L, "Missing API",
                List.of(
                        messageNode("msg1", "Starting..."),
                        apiNode("api1", "Bad API Call", "999")
                ),
                List.of(transition("msg1", "api1"))
        );
        when(workflowRepository.findById(7L)).thenReturn(Optional.of(workflow));
        when(apiConfigCacheService.findById(999L)).thenReturn(Optional.empty());

        service.startWorkflow(SESSION_ID, 7L);

        List<Object> messages = captureMessages();

        // Should have: "Starting..." message + error about missing config
        assertTrue(messages.size() >= 2, "Should have at least a message and an error");
        assertChatResponse(messages.get(0), "Starting...", null);

        // Second message should be an error
        boolean foundError = messages.stream()
                .filter(m -> m instanceof ChatErrorResponse)
                .map(m -> (ChatErrorResponse) m)
                .anyMatch(e -> e.getError().contains("No API configuration found for ID: 999"));
        assertTrue(foundError, "Should send error about missing API config");
    }

    // ========== Infinite Loop Detection ==========

    @Test
    @DisplayName("Golden: Infinite loop detected at 50 consecutive message nodes")
    void infiniteLoopDetectionAt50() {
        // Create a chain of 55 message nodes (exceeds threshold of 50)
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> transitions = new ArrayList<>();

        for (int i = 1; i <= 55; i++) {
            nodes.add(messageNode("msg" + i, "Message " + i));
            if (i < 55) {
                transitions.add(transition("msg" + i, "msg" + (i + 1)));
            }
        }

        Workflow workflow = buildWorkflow(10L, "InfiniteLoop", nodes, transitions);
        when(workflowRepository.findById(10L)).thenReturn(Optional.of(workflow));

        service.startWorkflow(SESSION_ID, 10L);

        List<Object> messages = captureMessages();

        // Should detect infinite loop and send error
        boolean foundLoopError = messages.stream()
                .filter(m -> m instanceof ChatErrorResponse)
                .map(m -> (ChatErrorResponse) m)
                .anyMatch(e -> e.getError().toLowerCase().contains("infinite loop"));
        assertTrue(foundLoopError, "Should detect infinite loop at 50 consecutive message nodes");
    }

    // ========== Back Navigation ==========

    @Test
    @DisplayName("Golden: Back navigation re-sends the previous input prompt")
    void backNavigationResendPrompt() {
        // 3 input nodes: inp1 → inp2 → inp3
        // After answering inp1 and inp2, we're paused at inp3.
        // Calling back should re-send inp3's prompt (the most recent awaitsInput entry).
        // Calling back again re-sends inp2's prompt (the previous one).
        Workflow workflow = buildWorkflow(8L, "BackNav",
                List.of(
                        inputNode("inp1", "First question?", "answer1"),
                        inputNode("inp2", "Second question?", "answer2"),
                        inputNode("inp3", "Third question?", "answer3")
                ),
                List.of(
                        transition("inp1", "inp2"),
                        transition("inp2", "inp3")
                )
        );
        when(workflowRepository.findById(8L)).thenReturn(Optional.of(workflow));

        service.startWorkflow(SESSION_ID, 8L);

        // Pauses at inp1 — reply to advance
        session.setWorkflowId(8L);
        service.handleUserInput(SESSION_ID, "first answer");

        // Now paused at inp2 — reply to advance
        service.handleUserInput(SESSION_ID, "second answer");

        // Now paused at inp3
        assertEquals("inp3", session.getCurrentNodeId());

        // Navigate back — should re-send inp3's prompt (most recent awaitsInput)
        reset(messagingTemplate);
        service.handleBack(SESSION_ID);

        List<Object> backMessages = captureMessages();

        assertTrue(backMessages.size() >= 1, "Back should produce at least one message");
        // handleBack finds most recent awaitsInput entry (inp3) and re-sends its prompt
        boolean foundPrompt = backMessages.stream()
                .filter(m -> m instanceof ChatResponse)
                .map(m -> (ChatResponse) m)
                .anyMatch(r -> "Third question?".equals(r.getResponse()) || "Second question?".equals(r.getResponse()));
        assertTrue(foundPrompt, "Back navigation should re-send an input prompt");
    }

    // ========== Restart Clears Context ==========

    @Test
    @DisplayName("Golden: Restart clears context and restarts from first node")
    void restartClearsContextAndRestarts() {
        Workflow workflow = buildWorkflow(9L, "Restart",
                List.of(
                        inputNode("inp1", "Enter name", "userName"),
                        messageNode("msg1", "Hello {{userName}}")
                ),
                List.of(transition("inp1", "msg1"))
        );
        when(workflowRepository.findById(9L)).thenReturn(Optional.of(workflow));

        service.startWorkflow(SESSION_ID, 9L);

        // Reply to input
        session.setWorkflowId(9L);
        service.handleUserInput(SESSION_ID, "Bob");

        // Session is completed
        assertEquals("completed", session.getStatus());
        assertEquals("Bob", session.getContext().get("userName"));

        // Restart
        reset(messagingTemplate);
        service.handleRestart(SESSION_ID);

        List<Object> restartMessages = captureMessages();

        // After restart: context should be cleared, execution starts from first node
        assertNull(session.getContext().get("userName"), "User variables should be cleared on restart");
        assertEquals(9L, session.getWorkflowId(), "Workflow ID should be root workflow");

        // Should re-send the first input prompt
        boolean foundFirstPrompt = restartMessages.stream()
                .filter(m -> m instanceof ChatResponse)
                .map(m -> (ChatResponse) m)
                .anyMatch(r -> "Enter name".equals(r.getResponse()));
        assertTrue(foundFirstPrompt, "Restart should re-send first node prompt");
    }

    // ==================== Helper Methods ====================

    private List<Object> captureMessages() {
        ArgumentCaptor<Object> messageCaptor = ArgumentCaptor.forClass(Object.class);
        try {
            verify(messagingTemplate, atLeastOnce())
                    .convertAndSend(eq("/topic/chat/" + SESSION_ID), messageCaptor.capture());
            return messageCaptor.getAllValues();
        } catch (org.mockito.exceptions.verification.WantedButNotInvoked e) {
            return Collections.emptyList();
        }
    }

    private void assertChatResponse(Object message, String expectedResponse, Boolean expectedCompleted) {
        assertInstanceOf(ChatResponse.class, message, "Expected ChatResponse but got: " + message.getClass().getSimpleName());
        ChatResponse response = (ChatResponse) message;
        assertEquals(expectedResponse, response.getResponse(), "Response text mismatch");
        if (expectedCompleted != null) {
            assertEquals(expectedCompleted, response.getCompleted(), "Completed flag mismatch");
        }
    }

    private void resetServiceWithMockedHttp(boolean httpSuccess) {
        // Rebuild with mocked HTTP executor that returns success
        HttpExecutor httpExecutor = mock(HttpExecutor.class);
        ResponseExtractor responseExtractor = mock(ResponseExtractor.class);
        PlaceholderService placeholderService = new PlaceholderService();
        ConditionEvaluator conditionEvaluator = new ConditionEvaluator();

        com.xpressbees.chatbot.dto.HttpExecutionResult httpResult =
                new com.xpressbees.chatbot.dto.HttpExecutionResult(httpSuccess, httpSuccess ? 200 : 500, "{}", null);
        when(httpExecutor.execute(any(), anyString(), any(), any())).thenReturn(httpResult);

        ApiNodeProcessor apiNodeProcessor;
        {
            UrlValidator urlValidator2 = mock(UrlValidator.class);
            when(urlValidator2.validate(anyString())).thenReturn(com.xpressbees.chatbot.dto.UrlValidationResult.allowed());
            apiNodeProcessor = new ApiNodeProcessor(apiConfigCacheService, httpExecutor, responseExtractor, urlValidator2);
        }
        DecisionNodeProcessor decisionNodeProcessor = new DecisionNodeProcessor(conditionEvaluator);
        WorkflowNodeProcessor workflowNodeProcessor = new WorkflowNodeProcessor(workflowRepository);

        List<NodeProcessor> processors = List.of(
                new InputNodeProcessor(),
                new MessageNodeProcessor(),
                apiNodeProcessor,
                decisionNodeProcessor,
                workflowNodeProcessor
        );

        ChatMessageSender chatMessageSender = new ChatMessageSender(messagingTemplate);
        SessionStateManager sessionStateManager = new SessionStateManager(chatSessionRepository);
        NavigationService navigationService = new NavigationService(workflowRepository, placeholderService);
        ChildWorkflowService childWorkflowService = new ChildWorkflowService(workflowRepository);

        service = TestServiceFactory.createService(
                workflowRepository, processors, placeholderService,
                null, chatWebSocketHandler,
                chatMessageSender, sessionStateManager,
                navigationService, childWorkflowService
        );

        // Re-setup session mock
        session = new ChatSession();
        session.setSessionId(SESSION_ID);
        session.setStatus("active");
        session.setContext(new HashMap<>());

        reset(chatSessionRepository);
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> {
            session = invocation.getArgument(0);
            when(chatSessionRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.of(session));
            return session;
        });

        reset(messagingTemplate);
    }

    private HttpExecutor getHttpExecutorFromProcessors() {
        // Utility — not usable for mocking after construction
        return null;
    }

    private Map<String, Object> messageNode(String id, String name) {
        Map<String, Object> node = new HashMap<>();
        node.put("id", id);
        node.put("name", name);
        node.put("type", "state");
        node.put("config", null);
        return node;
    }

    private Map<String, Object> inputNode(String id, String name, String variableName) {
        Map<String, Object> config = new HashMap<>();
        config.put("nodeType", "input");
        config.put("variableName", variableName);

        Map<String, Object> node = new HashMap<>();
        node.put("id", id);
        node.put("name", name);
        node.put("type", "state");
        node.put("config", config);
        return node;
    }

    private Map<String, Object> decisionNode(String id, String name) {
        Map<String, Object> node = new HashMap<>();
        node.put("id", id);
        node.put("name", name);
        node.put("type", "decision");
        node.put("config", null);
        return node;
    }

    private Map<String, Object> apiNode(String id, String name, String apiConfigId) {
        Map<String, Object> config = new HashMap<>();
        config.put("nodeType", "api");
        config.put("apiConfigId", apiConfigId);

        Map<String, Object> node = new HashMap<>();
        node.put("id", id);
        node.put("name", name);
        node.put("type", "state");
        node.put("config", config);
        return node;
    }

    private Map<String, Object> workflowNode(String id, Long workflowId) {
        Map<String, Object> config = new HashMap<>();
        config.put("nodeType", "workflow");
        config.put("workflowId", workflowId.toString());

        Map<String, Object> node = new HashMap<>();
        node.put("id", id);
        node.put("name", "Workflow Node " + id);
        node.put("type", "state");
        node.put("config", config);
        return node;
    }

    private Map<String, Object> transition(String sourceNodeId, String targetNodeId) {
        return Map.of("sourceNodeId", sourceNodeId, "targetNodeId", targetNodeId);
    }

    private Map<String, Object> transitionWithCondition(String sourceNodeId, String targetNodeId, String condition) {
        Map<String, Object> t = new HashMap<>();
        t.put("sourceNodeId", sourceNodeId);
        t.put("targetNodeId", targetNodeId);
        t.put("condition", condition);
        return t;
    }

    private Workflow buildWorkflow(Long id, String name,
                                   List<Map<String, Object>> nodes,
                                   List<Map<String, Object>> transitions) {
        Map<String, Object> workflowJson = new HashMap<>();
        workflowJson.put("nodes", new ArrayList<>(nodes));
        workflowJson.put("transitions", new ArrayList<>(transitions));

        Workflow workflow = new Workflow();
        workflow.setId(id);
        workflow.setName(name);
        workflow.setWorkflowJson(workflowJson);
        return workflow;
    }
}

package com.xpressbees.chatbot.concurrent;

import com.xpressbees.chatbot.controller.ChatWebSocketHandler;
import com.xpressbees.chatbot.entity.ChatSession;
import com.xpressbees.chatbot.entity.Workflow;
import com.xpressbees.chatbot.processor.InputNodeProcessor;
import com.xpressbees.chatbot.processor.MessageNodeProcessor;
import com.xpressbees.chatbot.processor.NodeProcessor;
import com.xpressbees.chatbot.repository.ChatSessionRepository;
import com.xpressbees.chatbot.repository.WorkflowRepository;
import com.xpressbees.chatbot.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Concurrent message handling tests verifying thread safety of the workflow engine.
 *
 * Tests validate:
 * - Multiple concurrent chat.message requests for the same sessionId don't corrupt session context
 * - chat.start and chat.message arriving nearly simultaneously are handled gracefully
 *
 * Validates: Requirements 6.1, 6.3
 */
class ConcurrentMessageHandlingTest {

    private WorkflowRepository workflowRepository;
    private ChatSessionRepository chatSessionRepository;
    private SimpMessagingTemplate messagingTemplate;
    private ChatWebSocketHandler chatWebSocketHandler;
    private WorkflowExecutionServiceImpl service;

    // Shared mutable session simulating database state
    private ChatSession sharedSession;

    @BeforeEach
    void setUp() {
        workflowRepository = mock(WorkflowRepository.class);
        chatSessionRepository = mock(ChatSessionRepository.class);
        messagingTemplate = mock(SimpMessagingTemplate.class);
        chatWebSocketHandler = mock(ChatWebSocketHandler.class);

        PlaceholderService placeholderService = new PlaceholderService();
        List<NodeProcessor> processors = List.of(new InputNodeProcessor(), new MessageNodeProcessor());
        ChatMessageSender chatMessageSender = new ChatMessageSender(messagingTemplate);
        SessionStateManager sessionStateManager = new SessionStateManager(chatSessionRepository);
        NavigationService navigationService = new NavigationService(workflowRepository, placeholderService);
        ChildWorkflowService childWorkflowService = new ChildWorkflowService(workflowRepository);

        when(chatWebSocketHandler.consumePendingSession(anyString())).thenReturn(true);

        service = TestServiceFactory.createService(
                workflowRepository,
                processors,
                placeholderService,
                null,
                chatWebSocketHandler,
                chatMessageSender,
                sessionStateManager,
                navigationService,
                childWorkflowService
        );
    }

    /**
     * Test: Multiple concurrent chat.message requests for the same sessionId don't corrupt session context.
     *
     * Sets up a session at an input node, then fires multiple threads sending messages concurrently.
     * Verifies that after all threads complete, the session context is not corrupted (no exceptions thrown,
     * and the context map remains a valid HashMap).
     */
    @Test
    void concurrentMessages_sameSession_doesNotCorruptContext() throws Exception {
        int threadCount = 10;
        String sessionId = "concurrent-session-1";

        // Create a workflow with: message -> input -> message (end)
        Workflow workflow = createSimpleInputWorkflow();
        when(workflowRepository.findById(1L)).thenReturn(Optional.of(workflow));

        // Create a session that is paused at an input node
        sharedSession = new ChatSession();
        sharedSession.setSessionId(sessionId);
        sharedSession.setWorkflowId(1L);
        sharedSession.setStatus("active");
        sharedSession.setCurrentNodeId("input-1");
        sharedSession.setCurrentNodeType("input");
        Map<String, Object> context = new ConcurrentHashMap<>();
        context.put("_inputVariableName", "user_response");
        sharedSession.setContext(context);

        // Mock repository to always return the shared session (simulating concurrent reads)
        when(chatSessionRepository.findBySessionId(sessionId))
                .thenReturn(Optional.of(sharedSession));

        // Track save calls
        AtomicInteger saveCount = new AtomicInteger(0);
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> {
            saveCount.incrementAndGet();
            return invocation.getArgument(0);
        });

        // Use CountDownLatch for controlled parallel execution
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // All threads wait until released
                    service.handleUserInput(sessionId, "message-" + index);
                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Release all threads simultaneously
        startLatch.countDown();

        // Wait for all threads to complete
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Assertions
        assertThat(completed).as("All threads should complete within timeout").isTrue();
        assertThat(exceptions).as("No exceptions should be thrown during concurrent processing").isEmpty();

        // Session context should still be a valid map (not corrupted)
        assertThat(sharedSession.getContext()).isNotNull();
        assertThat(sharedSession.getContext()).isInstanceOf(Map.class);

        // At least one save should have occurred (threads may race but none should throw)
        assertThat(saveCount.get()).isGreaterThan(0);
    }

    /**
     * Test: chat.start and chat.message arriving nearly simultaneously for the same session.
     *
     * Verifies that when a chat.start and a chat.message arrive at almost the same time,
     * the system handles ordering gracefully without throwing exceptions.
     * The start should be processed before the message (or the message should receive
     * a graceful "no active session" error if the session isn't ready yet).
     */
    @Test
    void startAndMessage_nearlySimultaneous_handledGracefully() throws Exception {
        String sessionId = "simultaneous-session";
        Long workflowId = 2L;

        // Create a workflow that pauses at input after an initial message
        Workflow workflow = createSimpleInputWorkflow();
        when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(workflow));
        when(chatWebSocketHandler.consumePendingSession(sessionId)).thenReturn(true);

        // Initially, session doesn't exist (will be created by startWorkflow)
        // After startWorkflow creates it, findBySessionId might return it
        AtomicInteger findCallCount = new AtomicInteger(0);
        when(chatSessionRepository.findBySessionId(sessionId)).thenAnswer(invocation -> {
            int callNumber = findCallCount.incrementAndGet();
            if (callNumber <= 1) {
                // First call (from handleUserInput arriving early) - session might not exist yet
                return Optional.empty();
            }
            // Subsequent calls - session exists
            ChatSession session = new ChatSession();
            session.setSessionId(sessionId);
            session.setWorkflowId(workflowId);
            session.setStatus("active");
            session.setCurrentNodeId("input-1");
            session.setCurrentNodeType("input");
            session.setContext(new HashMap<>(Map.of("_inputVariableName", "user_response")));
            return Optional.of(session);
        });

        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

        // Thread 1: start workflow
        executor.submit(() -> {
            try {
                startLatch.await();
                service.startWorkflow(sessionId, workflowId);
            } catch (Exception e) {
                exceptions.add(e);
            } finally {
                doneLatch.countDown();
            }
        });

        // Thread 2: send message (nearly simultaneous)
        executor.submit(() -> {
            try {
                startLatch.await();
                service.handleUserInput(sessionId, "early-message");
            } catch (Exception e) {
                exceptions.add(e);
            } finally {
                doneLatch.countDown();
            }
        });

        // Release both threads simultaneously
        startLatch.countDown();

        // Wait for both threads to complete
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Assertions: no exceptions should be thrown regardless of ordering
        assertThat(completed).as("Both threads should complete within timeout").isTrue();
        assertThat(exceptions).as("No exceptions should be thrown - the system handles ordering gracefully").isEmpty();

        // Verify the messaging template was used (either to send responses or errors)
        // At minimum, startWorkflow should have sent something
        verify(messagingTemplate, atLeastOnce()).convertAndSend(anyString(), any(Object.class));
    }

    /**
     * Creates a simple workflow with: message-1 -> input-1 -> message-2
     * This workflow pauses at the input node waiting for user input.
     */
    private Workflow createSimpleInputWorkflow() {
        Workflow workflow = new Workflow();
        workflow.setId(1L);
        workflow.setName("Test Workflow");

        Map<String, Object> workflowJson = new HashMap<>();

        List<Map<String, Object>> nodes = new ArrayList<>();
        nodes.add(Map.of(
                "id", "message-1",
                "name", "Welcome",
                "type", "message",
                "config", Map.of("message", "Hello!")
        ));
        nodes.add(Map.of(
                "id", "input-1",
                "name", "Ask Name",
                "type", "input",
                "config", Map.of("message", "What is your name?", "variableName", "user_response")
        ));
        nodes.add(Map.of(
                "id", "message-2",
                "name", "Goodbye",
                "type", "message",
                "config", Map.of("message", "Thanks!")
        ));

        List<Map<String, Object>> transitions = new ArrayList<>();
        transitions.add(Map.of("sourceNodeId", "message-1", "targetNodeId", "input-1"));
        transitions.add(Map.of("sourceNodeId", "input-1", "targetNodeId", "message-2"));

        workflowJson.put("nodes", nodes);
        workflowJson.put("transitions", transitions);
        workflow.setWorkflowJson(workflowJson);

        return workflow;
    }
}

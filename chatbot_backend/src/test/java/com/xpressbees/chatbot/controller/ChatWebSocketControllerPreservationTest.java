package com.xpressbees.chatbot.controller;

import com.xpressbees.chatbot.dto.ChatBackRequest;
import com.xpressbees.chatbot.dto.ChatMessageRequest;
import com.xpressbees.chatbot.dto.ChatStartRequest;
import com.xpressbees.chatbot.service.BufferedMessageSender;
import com.xpressbees.chatbot.service.ConnectionRegistry;
import com.xpressbees.chatbot.service.WorkflowExecutionService;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.AfterTry;
import net.jqwik.api.lifecycle.BeforeTry;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

import java.util.UUID;

import static org.mockito.Mockito.*;

/**
 * Preservation Property Tests — Normal Message Processing Remains Unaffected.
 *
 * These tests verify on UNFIXED code that:
 * 1. Normal controller method calls (startWorkflow, handleMessage, handleBack, handleRestart)
 *    delegate to the service layer without triggering the exception handler.
 * 2. When the service layer catches and handles its own exceptions internally,
 *    the controller-level handleException is never invoked.
 *
 * EXPECTED OUTCOME: Tests PASS on both unfixed and fixed code (confirms baseline behavior
 * that must be preserved after the fix is applied).
 *
 * Validates: Requirements 3.1, 3.2, 3.3
 */
class ChatWebSocketControllerPreservationTest {

    @Mock
    private WorkflowExecutionService workflowExecutionService;

    @Mock
    private ConnectionRegistry connectionRegistry;

    @Mock
    private BufferedMessageSender bufferedMessageSender;

    @Mock
    private SimpMessageHeaderAccessor headerAccessor;

    private ChatWebSocketController controller;
    private AutoCloseable mocks;

    @BeforeTry
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        controller = new ChatWebSocketController(workflowExecutionService, connectionRegistry, bufferedMessageSender);
    }

    @AfterTry
    void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Property 2a: Preservation — Normal startWorkflow Delegates to Service Layer
    //
    // For all valid ChatStartRequest inputs where the service method executes
    // normally (no exception thrown), the controller delegates to
    // workflowExecutionService.startWorkflow() and handleException is never invoked.
    //
    // EXPECTED OUTCOME: PASSES on unfixed code (normal flow works correctly)
    //
    // Validates: Requirements 3.1, 3.2
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Property 2a: For all valid ChatStartRequest inputs where the service method
     * executes normally, the controller delegates to workflowExecutionService.startWorkflow()
     * and the exception handler is never triggered.
     *
     * Validates: Requirements 3.1, 3.2
     */
    @Property(tries = 50)
    @Tag("exceptionfix")
    @Tag("preservation")
    void startWorkflowDelegatesToServiceWithoutTriggeringExceptionHandler(
            @ForAll("sessionIds") String sessionId,
            @ForAll("workflowIds") Long workflowId,
            @ForAll("stompSessionIds") String stompSessionId) {

        // Arrange: configure header accessor
        when(headerAccessor.getSessionId()).thenReturn(stompSessionId);

        // Arrange: build request
        ChatStartRequest request = new ChatStartRequest();
        request.setSessionId(sessionId);
        request.setWorkflowId(workflowId);

        // Act: invoke the controller method directly (simulates normal message processing)
        controller.startWorkflow(request, headerAccessor);

        // Assert: service layer was called with correct arguments
        verify(workflowExecutionService).startWorkflow(sessionId, workflowId);

        // Assert: connection registry interactions happened as expected
        verify(connectionRegistry).recordActivity(stompSessionId);
        verify(connectionRegistry).associateApplicationSession(stompSessionId, sessionId);

        // Assert: no exception handling occurred (handleException produces no side effects
        // on unfixed code, and should not be reachable in the normal flow regardless)
        verifyNoMoreInteractions(workflowExecutionService);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Property 2b: Preservation — Normal handleMessage Delegates to Service Layer
    //
    // For all valid ChatMessageRequest inputs where the service method executes
    // normally, the controller delegates to workflowExecutionService.handleUserInput()
    // and handleException is never invoked.
    //
    // EXPECTED OUTCOME: PASSES on unfixed code
    //
    // Validates: Requirements 3.1, 3.2
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Property 2b: For all valid ChatMessageRequest inputs where the service method
     * executes normally, the controller delegates to workflowExecutionService.handleUserInput().
     *
     * Validates: Requirements 3.1, 3.2
     */
    @Property(tries = 50)
    @Tag("exceptionfix")
    @Tag("preservation")
    void handleMessageDelegatesToServiceWithoutTriggeringExceptionHandler(
            @ForAll("sessionIds") String sessionId,
            @ForAll("messages") String message,
            @ForAll("stompSessionIds") String stompSessionId) {

        // Arrange
        when(headerAccessor.getSessionId()).thenReturn(stompSessionId);

        ChatMessageRequest request = new ChatMessageRequest();
        request.setSessionId(sessionId);
        request.setMessage(message);

        // Act
        controller.handleMessage(request, headerAccessor);

        // Assert: service layer was called with correct arguments
        verify(workflowExecutionService).handleUserInput(sessionId, message);
        verify(connectionRegistry).recordActivity(stompSessionId);

        // Assert: no additional/unexpected interactions
        verifyNoMoreInteractions(workflowExecutionService);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Property 2c: Preservation — Normal handleBack Delegates to Service Layer
    //
    // For all valid ChatBackRequest inputs where the service method executes normally,
    // the controller delegates to workflowExecutionService.handleBack()
    // and handleException is never invoked.
    //
    // EXPECTED OUTCOME: PASSES on unfixed code
    //
    // Validates: Requirements 3.1, 3.2
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Property 2c: For all valid ChatBackRequest inputs where the service method
     * executes normally, the controller delegates to workflowExecutionService.handleBack().
     *
     * Validates: Requirements 3.1, 3.2
     */
    @Property(tries = 50)
    @Tag("exceptionfix")
    @Tag("preservation")
    void handleBackDelegatesToServiceWithoutTriggeringExceptionHandler(
            @ForAll("sessionIds") String sessionId,
            @ForAll("stompSessionIds") String stompSessionId) {

        // Arrange
        when(headerAccessor.getSessionId()).thenReturn(stompSessionId);

        ChatBackRequest request = new ChatBackRequest();
        request.setSessionId(sessionId);

        // Act
        controller.handleBack(request, headerAccessor);

        // Assert: service layer was called with correct arguments
        verify(workflowExecutionService).handleBack(sessionId);
        verify(connectionRegistry).recordActivity(stompSessionId);

        // Assert: no additional/unexpected interactions
        verifyNoMoreInteractions(workflowExecutionService);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Property 2d: Preservation — Normal handleRestart Delegates to Service Layer
    //
    // For all valid ChatBackRequest inputs where the service method executes normally,
    // the controller delegates to workflowExecutionService.handleRestart()
    // and handleException is never invoked.
    //
    // EXPECTED OUTCOME: PASSES on unfixed code
    //
    // Validates: Requirements 3.1, 3.2
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Property 2d: For all valid ChatBackRequest inputs where the service method
     * executes normally, the controller delegates to workflowExecutionService.handleRestart().
     *
     * Validates: Requirements 3.1, 3.2
     */
    @Property(tries = 50)
    @Tag("exceptionfix")
    @Tag("preservation")
    void handleRestartDelegatesToServiceWithoutTriggeringExceptionHandler(
            @ForAll("sessionIds") String sessionId,
            @ForAll("stompSessionIds") String stompSessionId) {

        // Arrange
        when(headerAccessor.getSessionId()).thenReturn(stompSessionId);

        ChatBackRequest request = new ChatBackRequest();
        request.setSessionId(sessionId);

        // Act
        controller.handleRestart(request, headerAccessor);

        // Assert: service layer was called with correct arguments
        verify(workflowExecutionService).handleRestart(sessionId);
        verify(connectionRegistry).recordActivity(stompSessionId);

        // Assert: no additional/unexpected interactions
        verifyNoMoreInteractions(workflowExecutionService);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Property 2e: Preservation — Service-Layer Internal Exceptions Do Not
    //              Reach Controller Exception Handler
    //
    // When the service layer catches and handles its own exceptions internally
    // (e.g., by calling sendError()), those exceptions do NOT propagate to the
    // controller's @MessageExceptionHandler. This test verifies that when
    // service methods complete normally (even if they handled errors internally),
    // the controller does not invoke handleException.
    //
    // EXPECTED OUTCOME: PASSES on unfixed code
    //
    // Validates: Requirements 3.1, 3.3
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Property 2e: When service methods execute without throwing (because they handle
     * errors internally), the controller's exception handler is never invoked.
     * This simulates the case where WorkflowExecutionServiceImpl catches its own
     * exceptions and calls sendError() internally.
     *
     * Validates: Requirements 3.1, 3.3
     */
    @Property(tries = 50)
    @Tag("exceptionfix")
    @Tag("preservation")
    void serviceLayerInternalErrorHandlingDoesNotTriggerControllerExceptionHandler(
            @ForAll("sessionIds") String sessionId,
            @ForAll("workflowIds") Long workflowId,
            @ForAll("stompSessionIds") String stompSessionId) {

        // Arrange: configure header accessor
        when(headerAccessor.getSessionId()).thenReturn(stompSessionId);

        // Arrange: service method completes normally (it handled any error internally)
        // doNothing() is the default for void methods, but explicit here for clarity
        doNothing().when(workflowExecutionService).startWorkflow(sessionId, workflowId);

        ChatStartRequest request = new ChatStartRequest();
        request.setSessionId(sessionId);
        request.setWorkflowId(workflowId);

        // Act: invoke the controller — no exception escapes
        controller.startWorkflow(request, headerAccessor);

        // Assert: The service was called (it handled its own error internally)
        verify(workflowExecutionService).startWorkflow(sessionId, workflowId);

        // Assert: The controller's exception handler was never triggered
        // (We verify this indirectly: if handleException were called, it would be a separate
        //  code path. Since we're testing the controller directly without the Spring framework
        //  exception-routing mechanism, the fact that no exception was thrown from the controller
        //  method confirms the exception handler path is NOT entered.)
        verifyNoMoreInteractions(workflowExecutionService);
    }

    // ──────────────────────────── Providers ────────────────────────────────────

    @Provide
    Arbitrary<String> sessionIds() {
        return Arbitraries.create(() -> UUID.randomUUID().toString());
    }

    @Provide
    Arbitrary<Long> workflowIds() {
        return Arbitraries.longs().between(1L, 10000L);
    }

    @Provide
    Arbitrary<String> stompSessionIds() {
        return Arbitraries.strings()
                .alpha().ofMinLength(8).ofMaxLength(16)
                .map(s -> "stomp-" + s);
    }

    @Provide
    Arbitrary<String> messages() {
        return Arbitraries.strings()
                .alpha().ofMinLength(1).ofMaxLength(100)
                .map(s -> "user-input-" + s);
    }
}

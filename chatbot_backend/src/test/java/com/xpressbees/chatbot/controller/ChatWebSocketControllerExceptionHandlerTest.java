package com.xpressbees.chatbot.controller;

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
 * Bug Condition Exploration Test — Empty Exception Handler Silently Swallows Exceptions.
 *
 * This test encodes the EXPECTED behavior: when an unhandled exception reaches
 * {@code @MessageExceptionHandler}, the handler should resolve the application session ID
 * and call {@code bufferedMessageSender.sendError(applicationSessionId, "An unexpected error occurred")}.
 *
 * On UNFIXED code, this test is EXPECTED TO FAIL because the handler body is empty
 * and never calls sendError(). Failure confirms the bug exists.
 *
 * Validates: Requirements 1.1, 1.2, 2.1, 2.2, 2.3
 */
class ChatWebSocketControllerExceptionHandlerTest {

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
    // Property 1: Bug Condition — Empty Exception Handler Silently Swallows Exceptions
    //
    // For any exception that reaches handleException() with a resolvable session,
    // the handler SHOULD call bufferedMessageSender.sendError(applicationSessionId,
    // "An unexpected error occurred"). On unfixed code, this never happens because
    // the handler body is empty.
    //
    // EXPECTED OUTCOME: FAILS on unfixed code (proves bug exists)
    //
    // Validates: Requirements 1.1, 1.2, 2.1, 2.2, 2.3
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Property 1: Bug Condition - Empty Exception Handler Silently Swallows Exceptions.
     *
     * Generates random exception types with random messages and verifies that
     * handleException() calls sendError() on the BufferedMessageSender.
     * This test MUST FAIL on unfixed code — the empty handler produces no side effects.
     *
     * Validates: Requirements 1.1, 1.2, 2.1, 2.2, 2.3
     */
    @Property(tries = 50)
    @Tag("Feature: exceptionfix, Property 1: Bug Condition")
    void handleExceptionShouldSendErrorToClient(
            @ForAll("exceptions") Exception exception,
            @ForAll("stompSessionIds") String stompSessionId,
            @ForAll("applicationSessionIds") String applicationSessionId) {

        // Arrange: mock header accessor to return a valid STOMP session ID
        when(headerAccessor.getSessionId()).thenReturn(stompSessionId);

        // Arrange: mock connection registry to resolve application session ID
        when(connectionRegistry.getApplicationSessionId(stompSessionId)).thenReturn(applicationSessionId);

        // Act: invoke the exception handler directly
        controller.handleException(exception, headerAccessor);

        // Assert: sendError SHOULD be called with the application session ID
        // On UNFIXED code, this will FAIL because the handler body is empty
        verify(bufferedMessageSender).sendError(applicationSessionId, "An unexpected error occurred");
    }

    // ──────────────────────────── Providers ────────────────────────────────────

    @Provide
    Arbitrary<Exception> exceptions() {
        Arbitrary<String> messages = Arbitraries.strings()
                .alpha().ofMinLength(3).ofMaxLength(50)
                .map(s -> "Error: " + s);

        Arbitrary<Integer> exceptionTypes = Arbitraries.of(0, 1, 2);

        return Combinators.combine(exceptionTypes, messages)
                .as((type, message) -> {
                    switch (type) {
                        case 0:
                            return new RuntimeException(message);
                        case 1:
                            return new NullPointerException(message);
                        case 2:
                            return new IllegalStateException(message);
                        default:
                            return new RuntimeException(message);
                    }
                });
    }

    @Provide
    Arbitrary<String> stompSessionIds() {
        return Arbitraries.strings()
                .alpha().ofMinLength(8).ofMaxLength(16)
                .map(s -> "stomp-" + s);
    }

    @Provide
    Arbitrary<String> applicationSessionIds() {
        return Arbitraries.create(() -> UUID.randomUUID().toString());
    }
}

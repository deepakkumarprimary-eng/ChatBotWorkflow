package com.xpressbees.chatbot.controller;

import com.xpressbees.chatbot.dto.ChatBackRequest;
import com.xpressbees.chatbot.dto.ChatMessageRequest;
import com.xpressbees.chatbot.dto.ChatStartRequest;
import com.xpressbees.chatbot.service.BufferedMessageSender;
import com.xpressbees.chatbot.service.ConnectionRegistry;
import com.xpressbees.chatbot.service.WorkflowExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

import static org.mockito.Mockito.*;

class ChatWebSocketControllerTest {

    @Mock
    private WorkflowExecutionService workflowExecutionService;

    @Mock
    private ConnectionRegistry connectionRegistry;

    @Mock
    private BufferedMessageSender bufferedMessageSender;

    @Mock
    private SimpMessageHeaderAccessor headerAccessor;

    private ChatWebSocketController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new ChatWebSocketController(workflowExecutionService, connectionRegistry, bufferedMessageSender);
    }

    @Test
    void testStartWorkflow() {
        String stompSessionId = "stomp-session-1";
        when(headerAccessor.getSessionId()).thenReturn(stompSessionId);

        ChatStartRequest request = new ChatStartRequest();
        request.setSessionId("app-session-1");
        request.setWorkflowId(1L);

        controller.startWorkflow(request, headerAccessor);

        verify(connectionRegistry).recordActivity(stompSessionId);
        verify(connectionRegistry).associateApplicationSession(stompSessionId, "app-session-1");
        verify(workflowExecutionService).startWorkflow("app-session-1", 1L);
    }

    @Test
    void testHandleMessage() {
        String stompSessionId = "stomp-session-1";
        when(headerAccessor.getSessionId()).thenReturn(stompSessionId);

        ChatMessageRequest request = new ChatMessageRequest();
        request.setSessionId("app-session-1");
        request.setMessage("Hello");

        controller.handleMessage(request, headerAccessor);

        verify(connectionRegistry).recordActivity(stompSessionId);
        verify(workflowExecutionService).handleUserInput("app-session-1", "Hello");
    }

    @Test
    void testHandleBack() {
        String stompSessionId = "stomp-session-1";
        when(headerAccessor.getSessionId()).thenReturn(stompSessionId);

        ChatBackRequest request = new ChatBackRequest();
        request.setSessionId("app-session-1");

        controller.handleBack(request, headerAccessor);

        verify(connectionRegistry).recordActivity(stompSessionId);
        verify(workflowExecutionService).handleBack("app-session-1");
    }

    @Test
    void testHandleRestart() {
        String stompSessionId = "stomp-session-1";
        when(headerAccessor.getSessionId()).thenReturn(stompSessionId);

        ChatBackRequest request = new ChatBackRequest();
        request.setSessionId("app-session-1");

        controller.handleRestart(request, headerAccessor);

        verify(connectionRegistry).recordActivity(stompSessionId);
        verify(workflowExecutionService).handleRestart("app-session-1");
    }

    @Test
    void testHandleException_withResolvableSession() {
        String stompSessionId = "stomp-session-1";
        String applicationSessionId = "app-session-1";
        when(headerAccessor.getSessionId()).thenReturn(stompSessionId);
        when(connectionRegistry.getApplicationSessionId(stompSessionId)).thenReturn(applicationSessionId);

        RuntimeException exception = new RuntimeException("Test error");

        controller.handleException(exception, headerAccessor);

        verify(bufferedMessageSender).sendError(applicationSessionId, "An unexpected error occurred");
    }

    @Test
    void testHandleException_withNullStompSession() {
        when(headerAccessor.getSessionId()).thenReturn(null);

        RuntimeException exception = new RuntimeException("Test error");

        controller.handleException(exception, headerAccessor);

        // Should not attempt to send error when session cannot be resolved
        verifyNoInteractions(bufferedMessageSender);
    }

    @Test
    void testHandleException_withUnresolvableApplicationSession() {
        String stompSessionId = "stomp-session-1";
        when(headerAccessor.getSessionId()).thenReturn(stompSessionId);
        when(connectionRegistry.getApplicationSessionId(stompSessionId)).thenReturn(null);

        RuntimeException exception = new RuntimeException("Test error");

        controller.handleException(exception, headerAccessor);

        // Should not attempt to send error when application session cannot be resolved
        verify(bufferedMessageSender, never()).sendError(any(), any());
    }
}

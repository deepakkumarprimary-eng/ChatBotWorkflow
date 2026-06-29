package com.xpressbees.chatbot.controller;

import com.xpressbees.chatbot.dto.ChatBackRequest;
import com.xpressbees.chatbot.dto.ChatMessageRequest;
import com.xpressbees.chatbot.dto.ChatStartRequest;
import com.xpressbees.chatbot.service.ConnectionRegistry;
import com.xpressbees.chatbot.service.WorkflowExecutionService;
import jakarta.validation.Valid;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

@Controller
public class ChatWebSocketController {

    private final WorkflowExecutionService workflowExecutionService;
    private final ConnectionRegistry connectionRegistry;

    public ChatWebSocketController(WorkflowExecutionService workflowExecutionService,
                                   ConnectionRegistry connectionRegistry) {
        this.workflowExecutionService = workflowExecutionService;
        this.connectionRegistry = connectionRegistry;
    }

    @MessageMapping("/chat.start")
    public void startWorkflow(@Valid ChatStartRequest request, SimpMessageHeaderAccessor headerAccessor) {
        String stompSessionId = headerAccessor.getSessionId();
        connectionRegistry.recordActivity(stompSessionId);
        connectionRegistry.associateApplicationSession(stompSessionId, request.getSessionId());
        workflowExecutionService.startWorkflow(request.getSessionId(), request.getWorkflowId());
    }

    @MessageMapping("/chat.message")
    public void handleMessage(@Valid ChatMessageRequest request, SimpMessageHeaderAccessor headerAccessor) {
        String stompSessionId = headerAccessor.getSessionId();
        connectionRegistry.recordActivity(stompSessionId);
        workflowExecutionService.handleUserInput(request.getSessionId(), request.getMessage());
    }

    @MessageMapping("/chat.back")
    public void handleBack(ChatBackRequest request, SimpMessageHeaderAccessor headerAccessor) {
        String stompSessionId = headerAccessor.getSessionId();
        connectionRegistry.recordActivity(stompSessionId);
        workflowExecutionService.handleBack(request.getSessionId());
    }

    @MessageMapping("/chat.restart")
    public void handleRestart(ChatBackRequest request, SimpMessageHeaderAccessor headerAccessor) {
        String stompSessionId = headerAccessor.getSessionId();
        connectionRegistry.recordActivity(stompSessionId);
        workflowExecutionService.handleRestart(request.getSessionId());
    }

    @MessageExceptionHandler
    public void handleException(Exception ex, SimpMessageHeaderAccessor headerAccessor) {
        // Errors are handled in the service layer via sendError
    }
}

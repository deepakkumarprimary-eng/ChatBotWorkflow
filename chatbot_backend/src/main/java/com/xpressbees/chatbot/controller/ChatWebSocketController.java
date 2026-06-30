package com.xpressbees.chatbot.controller;

import com.xpressbees.chatbot.dto.ChatBackRequest;
import com.xpressbees.chatbot.dto.ChatMessageRequest;
import com.xpressbees.chatbot.dto.ChatStartRequest;
import com.xpressbees.chatbot.service.BufferedMessageSender;
import com.xpressbees.chatbot.service.ConnectionRegistry;
import com.xpressbees.chatbot.service.WorkflowExecutionService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

@Controller
public class ChatWebSocketController {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketController.class);

    private final WorkflowExecutionService workflowExecutionService;
    private final ConnectionRegistry connectionRegistry;
    private final BufferedMessageSender bufferedMessageSender;

    public ChatWebSocketController(WorkflowExecutionService workflowExecutionService,
                                   ConnectionRegistry connectionRegistry,
                                   BufferedMessageSender bufferedMessageSender) {
        this.workflowExecutionService = workflowExecutionService;
        this.connectionRegistry = connectionRegistry;
        this.bufferedMessageSender = bufferedMessageSender;
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
        String stompSessionId = headerAccessor.getSessionId();
        String applicationSessionId = null;

        if (stompSessionId != null) {
            applicationSessionId = connectionRegistry.getApplicationSessionId(stompSessionId);
        }

        log.error("Unhandled exception in WebSocket handler: sessionId={}, stompSessionId={}, error={}",
                applicationSessionId != null ? applicationSessionId : "unknown",
                stompSessionId != null ? stompSessionId : "unknown",
                ex.getMessage(),
                ex);

        if (applicationSessionId != null) {
            bufferedMessageSender.sendError(applicationSessionId, "An unexpected error occurred");
        }
    }
}

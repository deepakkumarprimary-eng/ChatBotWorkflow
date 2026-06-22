package com.xpressbees.chatbot.controller;

import com.xpressbees.chatbot.dto.ChatBackRequest;
import com.xpressbees.chatbot.dto.ChatMessageRequest;
import com.xpressbees.chatbot.dto.ChatStartRequest;
import com.xpressbees.chatbot.service.WorkflowExecutionService;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;

@Controller
@CrossOrigin("*")
public class ChatWebSocketController {

    private final WorkflowExecutionService workflowExecutionService;

    public ChatWebSocketController(WorkflowExecutionService workflowExecutionService) {
        this.workflowExecutionService = workflowExecutionService;
    }

    @MessageMapping("/chat.start")
    public void startWorkflow(ChatStartRequest request) {
        workflowExecutionService.startWorkflow(request.getSessionId(), request.getWorkflowId());
    }

    @MessageMapping("/chat.message")
    public void handleMessage(ChatMessageRequest request) {
        workflowExecutionService.handleUserInput(request.getSessionId(), request.getMessage());
    }

    @MessageMapping("/chat.back")
    public void handleBack(ChatBackRequest request) {
        workflowExecutionService.handleBack(request.getSessionId());
    }

    @MessageMapping("/chat.restart")
    public void handleRestart(ChatBackRequest request) {
        workflowExecutionService.handleRestart(request.getSessionId());
    }

    @MessageExceptionHandler
    public void handleException(Exception ex, SimpMessageHeaderAccessor headerAccessor) {
        // Errors are handled in the service layer via sendError
    }
}

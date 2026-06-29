package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.dto.ChatErrorResponse;
import com.xpressbees.chatbot.dto.ChatResponse;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Unified WebSocket message delivery service.
 * The sole point of access to SimpMessagingTemplate for chat messages.
 */
@Service
public class ChatMessageSender {

    private final SimpMessagingTemplate messagingTemplate;

    public ChatMessageSender(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Sends a ChatResponse to the session's topic.
     */
    public void sendResponse(String sessionId, ChatResponse response) {
        messagingTemplate.convertAndSend("/topic/chat/" + sessionId, response);
    }

    /**
     * Sends a ChatErrorResponse to the session's topic.
     */
    public void sendError(String sessionId, String errorMessage) {
        ChatErrorResponse error = new ChatErrorResponse(errorMessage, sessionId);
        messagingTemplate.convertAndSend("/topic/chat/" + sessionId, error);
    }
}

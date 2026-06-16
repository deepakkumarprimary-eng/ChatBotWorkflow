package com.xpressbees.chatbot.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

import com.xpressbees.chatbot.dto.WorkflowSummaryDto;
import com.xpressbees.chatbot.entity.ChatSession;
import com.xpressbees.chatbot.repository.ChatSessionRepository;
import com.xpressbees.chatbot.repository.WorkflowRepository;

@Controller
public class ChatWebSocketHandler {

    private final WorkflowRepository workflowRepository;
    private final ChatSessionRepository chatSessionRepository;

    public ChatWebSocketHandler(WorkflowRepository workflowRepository,
                                 ChatSessionRepository chatSessionRepository) {
        this.workflowRepository = workflowRepository;
        this.chatSessionRepository = chatSessionRepository;
    }

    @SubscribeMapping("/chat.init")
    public Map<String, Object> onChatInit() {
        // Generate session
        ChatSession session = new ChatSession();
        session.setSessionId(UUID.randomUUID().toString());
        session.setWorkflowId(0L); // Will be set when chat.start is called
        session.setStatus("active");
        session.setContext(new HashMap<>());
        chatSessionRepository.save(session);

        // Get workflow list
        List<WorkflowSummaryDto> workflows = workflowRepository.findAll().stream()
                .map(w -> new WorkflowSummaryDto(w.getId(), w.getName()))
                .collect(Collectors.toList());

        // Return sessionId + workflows
        Map<String, Object> response = new HashMap<>();
        response.put("sessionId", session.getSessionId());
        response.put("workflows", workflows);
        return response;
    }
}

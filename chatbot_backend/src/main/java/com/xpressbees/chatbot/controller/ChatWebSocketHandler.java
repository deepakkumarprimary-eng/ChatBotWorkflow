package com.xpressbees.chatbot.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;
import com.xpressbees.chatbot.dto.WorkflowSummaryDto;
import com.xpressbees.chatbot.repository.WorkflowRepository;
import com.xpressbees.chatbot.service.PendingSessionStore;

@Controller
public class ChatWebSocketHandler {

    private final WorkflowRepository workflowRepository;
    private final PendingSessionStore pendingSessionStore;

    public ChatWebSocketHandler(WorkflowRepository workflowRepository,
                                PendingSessionStore pendingSessionStore) {
        this.workflowRepository = workflowRepository;
        this.pendingSessionStore = pendingSessionStore;
    }

    @SubscribeMapping("/chat.init")
    public Map<String, Object> onChatInit() {
        // Generate session ID and register in Redis-backed store (no DB write)
        String sessionId = UUID.randomUUID().toString();
        pendingSessionStore.register(sessionId);

        // Get workflow list
        List<WorkflowSummaryDto> workflows = workflowRepository.findAll().stream()
                .map(w -> new WorkflowSummaryDto(w.getId(), w.getName()))
                .collect(Collectors.toList());

        // Return sessionId + workflows
        Map<String, Object> response = new HashMap<>();
        response.put("sessionId", sessionId);
        response.put("workflows", workflows);
        return response;
    }

    /**
     * Atomically consumes (removes) a pending session ID from Redis.
     *
     * @param sessionId the session ID to consume
     * @return true if the session ID was found and removed, false otherwise
     */
    public boolean consumePendingSession(String sessionId) {
        return pendingSessionStore.consume(sessionId);
    }
}

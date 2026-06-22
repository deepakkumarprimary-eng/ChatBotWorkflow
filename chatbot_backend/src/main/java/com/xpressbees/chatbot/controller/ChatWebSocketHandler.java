package com.xpressbees.chatbot.controller;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;

import com.xpressbees.chatbot.dto.WorkflowSummaryDto;
import com.xpressbees.chatbot.repository.WorkflowRepository;

@Controller
@CrossOrigin("*")
public class ChatWebSocketHandler {

    private final WorkflowRepository workflowRepository;
    private final ConcurrentHashMap<String, Instant> pendingSessions = new ConcurrentHashMap<>();

    public ChatWebSocketHandler(WorkflowRepository workflowRepository) {
        this.workflowRepository = workflowRepository;
    }

    @SubscribeMapping("/chat.init")
    public Map<String, Object> onChatInit() {
        // Generate session ID and store in pending map (no DB write)
        String sessionId = UUID.randomUUID().toString();
        pendingSessions.put(sessionId, Instant.now());

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
     * Atomically removes a pending session ID from the map.
     *
     * @param sessionId the session ID to consume
     * @return true if the session ID was found and removed, false otherwise
     */
    public boolean consumePendingSession(String sessionId) {
        return pendingSessions.remove(sessionId) != null;
    }
}

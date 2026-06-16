package com.xpressbees.chatbot.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

import com.xpressbees.chatbot.dto.WorkflowSummaryDto;
import com.xpressbees.chatbot.repository.WorkflowRepository;

@Controller
public class ChatWebSocketHandler {

    private final WorkflowRepository workflowRepository;

    public ChatWebSocketHandler(WorkflowRepository workflowRepository) {
        this.workflowRepository = workflowRepository;
    }

    @SubscribeMapping("/topic/workflows")
    public List<WorkflowSummaryDto> onSubscribeWorkflows() {
        return workflowRepository.findAll().stream().map(w -> new WorkflowSummaryDto(w.getId(), w.getName())).collect(Collectors.toList());
    }
}

package com.xpressbees.chatbot.processor;

import com.xpressbees.chatbot.dto.NodeProcessingResult;
import com.xpressbees.chatbot.entity.ChatSession;
import com.xpressbees.chatbot.service.PlaceholderService;

import java.util.Map;

public interface NodeProcessor {
    boolean canHandle(Map<String, Object> node);
    NodeProcessingResult process(Map<String, Object> node, ChatSession session, PlaceholderService placeholderService);
}

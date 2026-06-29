package com.xpressbees.chatbot.processor;

import com.xpressbees.chatbot.dto.NodeProcessingResult;
import com.xpressbees.chatbot.entity.ChatSession;
import com.xpressbees.chatbot.service.PlaceholderService;

import java.util.Map;

public interface NodeProcessor {
    boolean canHandle(Map<String, Object> node);
    NodeProcessingResult process(Map<String, Object> node, ChatSession session, PlaceholderService placeholderService, Map<String, Object> workflowJson);

    /**
     * Returns the node type key this processor handles.
     * Subclasses override to declare their type for O(1) registry lookup.
     */
    default String getNodeType() {
        return null;
    }
}

package com.xpressbees.chatbot.processor;

import com.xpressbees.chatbot.dto.ChatResponse;
import com.xpressbees.chatbot.dto.NodeProcessingResult;
import com.xpressbees.chatbot.entity.ChatSession;
import com.xpressbees.chatbot.service.PlaceholderService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Order(2)
public class MessageNodeProcessor implements NodeProcessor {

    @Override
    public boolean canHandle(Map<String, Object> node) {
        String type = (String) node.get("type");
        if (!"state".equals(type)) {
            return false;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) node.get("config");
        return config == null || !config.containsKey("nodeType");
    }

    @Override
    public NodeProcessingResult process(Map<String, Object> node, ChatSession session,
                                         PlaceholderService placeholderService) {
        String name = (String) node.get("name");
        String response = placeholderService.resolve(name, session.getContext());
        ChatResponse chatResponse = new ChatResponse(node, response, session.getSessionId());
        return new NodeProcessingResult(NodeProcessingResult.Action.CONTINUE, chatResponse);
    }
}

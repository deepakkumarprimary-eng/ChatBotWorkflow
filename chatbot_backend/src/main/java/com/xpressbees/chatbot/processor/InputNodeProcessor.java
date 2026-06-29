package com.xpressbees.chatbot.processor;

import com.xpressbees.chatbot.dto.ChatResponse;
import com.xpressbees.chatbot.dto.NodeProcessingResult;
import com.xpressbees.chatbot.entity.ChatSession;
import com.xpressbees.chatbot.service.PlaceholderService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Order(1)
public class InputNodeProcessor implements NodeProcessor {

    @Override
    public String getNodeType() {
        return "input";
    }

    @Override
    public boolean canHandle(Map<String, Object> node) {
        String type = (String) node.get("type");
        if (!"state".equals(type)) {
            return false;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) node.get("config");
        return config != null && "input".equals(config.get("nodeType"));
    }

    @SuppressWarnings("unchecked")
    @Override
    public NodeProcessingResult process(Map<String, Object> node, ChatSession session,
                                         PlaceholderService placeholderService, Map<String, Object> workflowJson) {
        String name = (String) node.get("name");
        String response = placeholderService.resolve(name, session.getContext());
        ChatResponse chatResponse = new ChatResponse(node, response, session.getSessionId());

        session.setCurrentNodeId((String) node.get("id"));
        session.setCurrentType((String) node.get("type"));
        session.setCurrentNodeType("input");

        // Extract config.variableName to determine context key for user reply storage
        String variableName = null;
        Map<String, Object> config = (Map<String, Object>) node.get("config");
        if (config != null) {
            variableName = (String) config.get("variableName");
        }

        // Fall back to node id if variableName is null or empty
        if (variableName == null || variableName.trim().isEmpty()) {
            variableName = (String) node.get("id");
        }

        // Store resolved variable name in context for the resume handler
        Map<String, Object> context = session.getContext();
        if (context == null) {
            context = new java.util.HashMap<>();
            session.setContext(context);
        }
        context.put("_inputVariableName", variableName);

        return new NodeProcessingResult(NodeProcessingResult.Action.PAUSE, chatResponse);
    }
}

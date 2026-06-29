package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.processor.MessageNodeProcessor;
import com.xpressbees.chatbot.processor.NodeProcessor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ProcessorRegistry {

    private final Map<String, NodeProcessor> registry;
    private final NodeProcessor fallbackProcessor;

    /**
     * Built at startup from injected List<NodeProcessor>.
     * Each processor declares what node type it handles via getNodeType().
     */
    public ProcessorRegistry(List<NodeProcessor> processors,
                             MessageNodeProcessor messageNodeProcessor) {
        this.fallbackProcessor = messageNodeProcessor;
        this.registry = new HashMap<>();
        for (NodeProcessor p : processors) {
            String type = p.getNodeType();
            if (type != null) {
                registry.put(type, p);
            }
        }
    }

    /**
     * Returns the processor for the given node type.
     * Falls back to MessageNodeProcessor for unknown types.
     */
    public NodeProcessor getProcessor(String nodeType) {
        return registry.getOrDefault(nodeType, fallbackProcessor);
    }
}

package com.xpressbees.chatbot.processor;

import com.xpressbees.chatbot.dto.NodeProcessingResult;
import com.xpressbees.chatbot.entity.ChatSession;
import com.xpressbees.chatbot.service.PlaceholderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for InputNodeProcessor.process() variable name resolution.
 * Validates: Requirements 2.1, 2.2, 2.3
 */
class InputNodeProcessorTest {

    private InputNodeProcessor processor;
    private PlaceholderService placeholderService;

    @BeforeEach
    void setUp() {
        processor = new InputNodeProcessor();
        placeholderService = new PlaceholderService();
    }

    private ChatSession createSession(String sessionId) {
        ChatSession session = new ChatSession();
        session.setSessionId(sessionId);
        session.setContext(new HashMap<>());
        return session;
    }

    private Map<String, Object> createNode(String nodeId, String name, String variableName) {
        Map<String, Object> node = new HashMap<>();
        node.put("id", nodeId);
        node.put("name", name);
        node.put("type", "state");

        Map<String, Object> config = new HashMap<>();
        config.put("nodeType", "input");
        if (variableName != null) {
            config.put("variableName", variableName);
        }
        node.put("config", config);

        return node;
    }

    @Test
    @DisplayName("When config.variableName = 'email', context contains _inputVariableName = 'email'")
    void process_withVariableNameEmail_storesEmailInContext() {
        ChatSession session = createSession("session-1");
        Map<String, Object> node = createNode("node-1", "Enter your email", "email");

        processor.process(node, session, placeholderService, null);

        assertEquals("email", session.getContext().get("_inputVariableName"));
    }

    @Test
    @DisplayName("When config.variableName = 'order_id', context contains _inputVariableName = 'order_id'")
    void process_withVariableNameOrderId_storesOrderIdInContext() {
        ChatSession session = createSession("session-2");
        Map<String, Object> node = createNode("node-2", "Enter your order ID", "order_id");

        processor.process(node, session, placeholderService, null);

        assertEquals("order_id", session.getContext().get("_inputVariableName"));
    }

    @Test
    @DisplayName("When config.variableName is null, context contains _inputVariableName = nodeId")
    void process_withNullVariableName_fallsBackToNodeId() {
        ChatSession session = createSession("session-3");
        Map<String, Object> node = createNode("node-abc", "Enter something", null);

        processor.process(node, session, placeholderService, null);

        assertEquals("node-abc", session.getContext().get("_inputVariableName"));
    }

    @Test
    @DisplayName("When config.variableName is empty string, context contains _inputVariableName = nodeId")
    void process_withEmptyVariableName_fallsBackToNodeId() {
        ChatSession session = createSession("session-4");
        Map<String, Object> node = createNode("node-xyz", "Enter something", "");

        processor.process(node, session, placeholderService, null);

        assertEquals("node-xyz", session.getContext().get("_inputVariableName"));
    }

    @Test
    @DisplayName("When config.variableName is whitespace only, context contains _inputVariableName = nodeId")
    void process_withWhitespaceVariableName_fallsBackToNodeId() {
        ChatSession session = createSession("session-5");
        Map<String, Object> node = createNode("node-ws", "Enter something", "   ");

        processor.process(node, session, placeholderService, null);

        assertEquals("node-ws", session.getContext().get("_inputVariableName"));
    }

    @Test
    @DisplayName("Existing PAUSE behavior: currentNodeType, currentNodeId, action, and response remain unchanged")
    void process_pauseBehavior_remainsUnchanged() {
        ChatSession session = createSession("session-6");
        Map<String, Object> node = createNode("node-pause", "Please enter your name", "user_name");

        NodeProcessingResult result = processor.process(node, session, placeholderService, null);

        // Verify PAUSE action
        assertEquals(NodeProcessingResult.Action.PAUSE, result.getAction());

        // Verify session state is set correctly
        assertEquals("input", session.getCurrentNodeType());
        assertEquals("node-pause", session.getCurrentNodeId());
        assertEquals("state", session.getCurrentType());

        // Verify response contains the prompt
        assertNotNull(result.getResponse());
        assertEquals("Please enter your name", result.getResponse().getResponse());
        assertEquals("session-6", result.getResponse().getSessionId());
    }
}

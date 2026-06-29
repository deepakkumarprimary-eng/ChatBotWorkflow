package com.xpressbees.chatbot.dto;

import com.xpressbees.chatbot.entity.ChatSession;
import com.xpressbees.chatbot.processor.InputNodeProcessor;
import com.xpressbees.chatbot.processor.MessageNodeProcessor;
import com.xpressbees.chatbot.service.PlaceholderService;
import net.jqwik.api.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Property 4: Response Format Consistency
 *
 * For any node processing result, the ChatResponse object SHALL contain:
 * (a) a node field identical to the original node map,
 * (b) a response field that is the node's name with placeholder substitution applied,
 * (c) a non-null sessionId string.
 *
 * Feature: websocket-workflow-execution, Property 4: Response Format Consistency
 */
class ChatResponseFormatPropertyTest {

    private final MessageNodeProcessor messageProcessor = new MessageNodeProcessor();
    private final InputNodeProcessor inputProcessor = new InputNodeProcessor();
    private final PlaceholderService placeholderService = new PlaceholderService();

    @Property(tries = 100)
    @Tag("Feature: websocket-workflow-execution, Property 4: Response Format Consistency")
    void messageNodeResponseContainsRequiredFields(@ForAll("messageNodes") Map<String, Object> node,
                                                    @ForAll("sessionIds") String sessionId) {
        ChatSession session = createSession(sessionId);
        NodeProcessingResult result = messageProcessor.process(node, session, placeholderService, null);

        ChatResponse response = result.getResponse();
        assert response.getNode() != null : "Node field must not be null";
        assert response.getNode() == node : "Node field must be the original node object";
        assert response.getResponse() != null : "Response field must not be null";
        assert response.getSessionId() != null : "SessionId must not be null";
        assert response.getSessionId().equals(sessionId) : "SessionId must match session";
    }

    @Property(tries = 100)
    @Tag("Feature: websocket-workflow-execution, Property 4: Response Format Consistency")
    void inputNodeResponseContainsRequiredFields(@ForAll("inputNodes") Map<String, Object> node,
                                                  @ForAll("sessionIds") String sessionId) {
        ChatSession session = createSession(sessionId);
        NodeProcessingResult result = inputProcessor.process(node, session, placeholderService, null);

        ChatResponse response = result.getResponse();
        assert response.getNode() != null : "Node field must not be null";
        assert response.getNode() == node : "Node field must be the original node object";
        assert response.getResponse() != null : "Response field must not be null";
        assert response.getSessionId() != null : "SessionId must not be null";
        assert response.getSessionId().equals(sessionId) : "SessionId must match session";
    }

    @Property(tries = 100)
    @Tag("Feature: websocket-workflow-execution, Property 4: Response Format Consistency")
    void responseFieldHasPlaceholderSubstitution(@ForAll("nodesWithPlaceholder") Map<String, Object> node,
                                                  @ForAll("mobileNumbers") String mobileNo) {
        ChatSession session = createSession("session-123");
        session.getContext().put("mobile_no", mobileNo);

        NodeProcessingResult result = messageProcessor.process(node, session, placeholderService, null);

        String responseText = result.getResponse().getResponse();
        assert !responseText.contains("{{mobile_no}}") :
                "Response should have placeholder replaced. Got: " + responseText;
        assert responseText.contains(mobileNo) :
                "Response should contain mobile number. Got: " + responseText;
    }

    private ChatSession createSession(String sessionId) {
        ChatSession session = new ChatSession();
        session.setSessionId(sessionId);
        session.setContext(new HashMap<>());
        session.setStatus("active");
        return session;
    }

    @Provide
    Arbitrary<Map<String, Object>> messageNodes() {
        return Arbitraries.of("Hello", "Welcome", "Goodbye").map(name -> {
            Map<String, Object> node = new HashMap<>();
            node.put("id", "1");
            node.put("name", name);
            node.put("type", "state");
            node.put("config", null);
            return node;
        });
    }

    @Provide
    Arbitrary<Map<String, Object>> inputNodes() {
        return Arbitraries.of("Enter mobile", "Your input", "Type here").map(name -> {
            Map<String, Object> node = new HashMap<>();
            node.put("id", "2");
            node.put("name", name);
            node.put("type", "state");
            node.put("config", Map.of("nodeType", "input"));
            return node;
        });
    }

    @Provide
    Arbitrary<Map<String, Object>> nodesWithPlaceholder() {
        return Arbitraries.of(
                "Your number is {{mobile_no}}",
                "{{mobile_no}} confirmed",
                "Call {{mobile_no}} now"
        ).map(name -> {
            Map<String, Object> node = new HashMap<>();
            node.put("id", "3");
            node.put("name", name);
            node.put("type", "state");
            node.put("config", null);
            return node;
        });
    }

    @Provide
    Arbitrary<String> sessionIds() {
        return Arbitraries.of("abc-123", "session-uuid-456", "test-session");
    }

    @Provide
    Arbitrary<String> mobileNumbers() {
        return Arbitraries.of("9876543210", "1234567890", "+91-9999");
    }
}

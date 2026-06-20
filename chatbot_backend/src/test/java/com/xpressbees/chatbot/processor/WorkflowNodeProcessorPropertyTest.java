package com.xpressbees.chatbot.processor;

import com.xpressbees.chatbot.dto.NodeProcessingResult;
import com.xpressbees.chatbot.entity.ChatSession;
import com.xpressbees.chatbot.repository.WorkflowRepository;
import com.xpressbees.chatbot.service.PlaceholderService;
import net.jqwik.api.*;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.mock;

/**
 * Property 1: Invalid workflowId rejection
 *
 * For any string value in the config map's workflowId field that cannot be
 * parsed as a Long, the WorkflowNodeProcessor SHALL return a NodeProcessingResult
 * with Action CONTINUE and a response message indicating the identifier is invalid.
 *
 * Validates: Requirements 1.4
 */
class WorkflowNodeProcessorPropertyTest {

    private final WorkflowRepository workflowRepository = mock(WorkflowRepository.class);
    private final PlaceholderService placeholderService = mock(PlaceholderService.class);
    private final WorkflowNodeProcessor processor = new WorkflowNodeProcessor(workflowRepository);

    @Property(tries = 100)
    @Tag("Feature: workflow-node, Property 1: Invalid workflowId rejection")
    void invalidWorkflowIdReturnsErrorWithContinueAction(
            @ForAll("nonNumericStrings") String invalidWorkflowId) {

        // Create node map with type="state", config={nodeType="workflow", workflowId=generatedString}
        Map<String, Object> config = new HashMap<>();
        config.put("nodeType", "workflow");
        config.put("workflowId", invalidWorkflowId);

        Map<String, Object> node = new HashMap<>();
        node.put("type", "state");
        node.put("id", "node-test-1");
        node.put("config", config);

        // Create a ChatSession with sessionId and empty context
        ChatSession session = new ChatSession();
        session.setSessionId("test-session-123");
        session.setContext(new HashMap<>());

        // Call processor
        NodeProcessingResult result = processor.process(node, session, placeholderService);

        // Assert result action is CONTINUE
        assert result.getAction() == NodeProcessingResult.Action.CONTINUE :
                "Expected CONTINUE action for invalid workflowId '" + invalidWorkflowId
                        + "', but got " + result.getAction();

        // Assert response message contains "invalid" (case insensitive)
        assert result.getResponse() != null :
                "Expected non-null response for invalid workflowId '" + invalidWorkflowId + "'";
        assert result.getResponse().getResponse().toLowerCase().contains("invalid") :
                "Expected response to contain 'invalid' for workflowId '" + invalidWorkflowId
                        + "', but got: " + result.getResponse().getResponse();
    }

    @Provide
    Arbitrary<String> nonNumericStrings() {
        return Arbitraries.strings()
                .ofMinLength(1)
                .ofMaxLength(50)
                .filter(s -> {
                    try {
                        Long.parseLong(s);
                        return false; // Reject strings parseable as Long
                    } catch (NumberFormatException e) {
                        return true; // Keep non-numeric strings
                    }
                });
    }
}

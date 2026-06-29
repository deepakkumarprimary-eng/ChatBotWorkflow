package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.config.WebSocketResilienceProperties;
import com.xpressbees.chatbot.controller.ChatWebSocketHandler;
import com.xpressbees.chatbot.processor.MessageNodeProcessor;
import com.xpressbees.chatbot.processor.NodeProcessor;
import com.xpressbees.chatbot.repository.WorkflowRepository;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * Test factory for creating WorkflowExecutionServiceImpl with the new constructor signature.
 * Bridges the gap between old test patterns and the refactored constructor that now takes
 * WorkflowCacheService and ProcessorRegistry instead of List&lt;NodeProcessor&gt;.
 */
public class TestServiceFactory {

    /**
     * Creates a WorkflowExecutionServiceImpl with the new constructor, wrapping a List&lt;NodeProcessor&gt;
     * into a ProcessorRegistry and providing a mock WorkflowCacheService that delegates to workflowRepository.
     */
    public static WorkflowExecutionServiceImpl createService(
            WorkflowRepository workflowRepository,
            List<NodeProcessor> processors,
            PlaceholderService placeholderService,
            InputValidationService inputValidationService,
            ChatWebSocketHandler chatWebSocketHandler,
            ChatMessageSender chatMessageSender,
            SessionStateManager sessionStateManager,
            NavigationService navigationService,
            ChildWorkflowService childWorkflowService) {

        WorkflowCacheService workflowCacheService = createMockCacheService(workflowRepository);
        ProcessorRegistry processorRegistry = createProcessorRegistry(processors);
        BufferedMessageSender bufferedMessageSender = createBufferedMessageSender(chatMessageSender);
        CorrelationIdManager correlationIdManager = new CorrelationIdManager();
        ExecutionTracker executionTracker = new ExecutionTracker();

        return new WorkflowExecutionServiceImpl(
                workflowRepository,
                workflowCacheService,
                processorRegistry,
                placeholderService,
                inputValidationService,
                chatWebSocketHandler,
                chatMessageSender,
                bufferedMessageSender,
                sessionStateManager,
                navigationService,
                childWorkflowService,
                correlationIdManager,
                executionTracker
        );
    }

    /**
     * Creates a BufferedMessageSender backed by the given ChatMessageSender.
     * Uses default resilience properties suitable for testing.
     */
    public static BufferedMessageSender createBufferedMessageSender(ChatMessageSender chatMessageSender) {
        WebSocketResilienceProperties properties = new WebSocketResilienceProperties();
        properties.setSendBufferSize(50);
        properties.setBufferDrainTimeoutSeconds(30);
        return new BufferedMessageSenderImpl(chatMessageSender, properties);
    }

    /**
     * Creates a mock WorkflowCacheService that delegates findById to the given repository.
     * Uses lenient stubbing to avoid UnnecessaryStubbingException in tests that don't call findById.
     */
    public static WorkflowCacheService createMockCacheService(WorkflowRepository workflowRepository) {
        WorkflowCacheService cacheService = mock(WorkflowCacheService.class);
        lenient().when(cacheService.findById(anyLong())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            return workflowRepository.findById(id);
        });
        return cacheService;
    }

    /**
     * Creates a ProcessorRegistry from the given list of processors.
     * Uses the first MessageNodeProcessor found as the fallback.
     */
    public static ProcessorRegistry createProcessorRegistry(List<NodeProcessor> processors) {
        MessageNodeProcessor fallback = processors.stream()
                .filter(p -> p instanceof MessageNodeProcessor)
                .map(p -> (MessageNodeProcessor) p)
                .findFirst()
                .orElse(new MessageNodeProcessor());

        return new ProcessorRegistry(processors, fallback);
    }
}

package com.xpressbees.chatbot;

import com.xpressbees.chatbot.controller.ChatWebSocketHandler;
import com.xpressbees.chatbot.entity.Workflow;
import com.xpressbees.chatbot.repository.WorkflowRepository;
import com.xpressbees.chatbot.service.PendingSessionStore;
import net.jqwik.api.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Bug Condition Exploration Test: Chat Init FK Violation on Premature Persistence
 *
 * Property: For any subscription to /app/chat.init, onChatInit() SHALL return a response
 * containing a valid UUID sessionId and a workflows list WITHOUT throwing an exception
 * or performing a database write with workflowId = 0L.
 *
 * Validates: Requirements 1.1, 1.2, 2.1
 *
 * EXPECTED OUTCOME on FIXED code: Test PASSES because onChatInit() no longer calls
 * chatSessionRepository.save() — it stores the session ID in an in-memory
 * ConcurrentHashMap instead, avoiding the FK constraint violation entirely.
 */
class ChatInitBugConditionTest {

    /**
     * **Validates: Requirements 1.1, 1.2, 2.1**
     *
     * Property: For any subscription to /app/chat.init, onChatInit() SHALL return a
     * response containing a valid UUID sessionId and a workflows list WITHOUT throwing
     * an exception or performing a database write with workflowId = 0L.
     *
     * After the fix, ChatWebSocketHandler no longer takes ChatSessionRepository —
     * it only requires WorkflowRepository. onChatInit() stores session IDs in memory
     * and never writes to the database, eliminating the FK violation.
     */
    @Property(tries = 10)
    void onChatInit_shallReturnValidResponse_withoutFKViolation(
            @ForAll("workflowLists") List<Workflow> workflows) {

        // Arrange: mock WorkflowRepository (the only dependency now)
        WorkflowRepository workflowRepository = mock(WorkflowRepository.class);

        // Mock: WorkflowRepository.findAll() returns the provided workflow list
        when(workflowRepository.findAll()).thenReturn(workflows);

        // Act: create the handler with FIXED code and call onChatInit()
        PendingSessionStore pendingSessionStore = mock(PendingSessionStore.class);
        when(pendingSessionStore.register(anyString())).thenReturn(true);
        ChatWebSocketHandler handler = new ChatWebSocketHandler(workflowRepository, pendingSessionStore);
        Map<String, Object> response = handler.onChatInit();

        // Assert: response contains "sessionId" key with a valid UUID string
        assertThat(response).containsKey("sessionId");
        String sessionId = (String) response.get("sessionId");
        assertThat(sessionId).isNotNull();
        // Validate it's a proper UUID format
        assertThat(UUID.fromString(sessionId)).isNotNull();

        // Assert: response contains "workflows" key with the workflow list
        assertThat(response).containsKey("workflows");
        assertThat(response.get("workflows")).isNotNull();

        // Assert: no database interaction occurred (no ChatSessionRepository means no DB write possible)
        // The fix guarantees no FK violation by design — there's no repository to call save() on
    }

    @Provide
    Arbitrary<List<Workflow>> workflowLists() {
        Arbitrary<Workflow> workflowArbitrary = Arbitraries.longs()
                .between(1L, 100L)
                .flatMap(id -> Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(20)
                        .map(name -> {
                            Workflow w = new Workflow();
                            w.setId(id);
                            w.setName(name);
                            return w;
                        }));

        return workflowArbitrary.list().ofMinSize(1).ofMaxSize(5);
    }
}

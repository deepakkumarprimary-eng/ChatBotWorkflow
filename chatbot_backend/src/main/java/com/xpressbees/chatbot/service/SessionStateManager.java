package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.dto.SaveResult;
import com.xpressbees.chatbot.entity.ChatSession;
import com.xpressbees.chatbot.repository.ChatSessionRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Optional;

@Service
public class SessionStateManager {

    private final ChatSessionRepository chatSessionRepository;

    public SessionStateManager(ChatSessionRepository chatSessionRepository) {
        this.chatSessionRepository = chatSessionRepository;
    }

    /**
     * Persists a ChatSession. Returns SaveResult indicating success or failure.
     */
    public SaveResult save(ChatSession session) {
        try {
            ChatSession saved = chatSessionRepository.save(session);
            return SaveResult.success(saved);
        } catch (DataAccessException e) {
            return SaveResult.failure("Failed to persist session state");
        }
    }

    /**
     * Loads a ChatSession by sessionId.
     * @return Optional containing the session, or empty if not found
     */
    public Optional<ChatSession> findBySessionId(String sessionId) {
        return chatSessionRepository.findBySessionId(sessionId);
    }

    /**
     * Creates and persists a new ChatSession with initial state.
     */
    public SaveResult createSession(String sessionId, Long workflowId) {
        ChatSession session = new ChatSession();
        session.setSessionId(sessionId);
        session.setWorkflowId(workflowId);
        session.setStatus("active");
        session.setContext(new HashMap<>());
        return save(session);
    }
}

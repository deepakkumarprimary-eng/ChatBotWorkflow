package com.xpressbees.chatbot.repository;

import com.xpressbees.chatbot.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
    Optional<ChatSession> findBySessionId(String sessionId);

    @Modifying
    @Query("UPDATE ChatSession c SET c.status = 'expired' WHERE c.status = 'active' AND c.updatedAt < :cutoff")
    int expireStaleSessions(@Param("cutoff") LocalDateTime cutoff);
}

package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.repository.ChatSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@ConditionalOnProperty(name = "chatbot.cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class StaleSessionCleanupService {

    private final ChatSessionRepository chatSessionRepository;
    private final long inactivityThresholdHours;

    public StaleSessionCleanupService(
            ChatSessionRepository chatSessionRepository,
            @Value("${chatbot.cleanup.inactivity-threshold-hours:24}") long inactivityThresholdHours) {
        this.chatSessionRepository = chatSessionRepository;
        this.inactivityThresholdHours = inactivityThresholdHours;
    }

    @Scheduled(fixedDelayString = "${chatbot.cleanup.interval-ms:3600000}")
    @Transactional
    public void cleanupStaleSessions() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(inactivityThresholdHours);
        int expiredCount = chatSessionRepository.expireStaleSessions(cutoff);
        log.info("Stale session cleanup completed: {} sessions expired", expiredCount);
    }
}

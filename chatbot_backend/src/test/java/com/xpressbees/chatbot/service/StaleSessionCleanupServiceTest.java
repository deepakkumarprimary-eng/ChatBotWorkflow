package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.repository.ChatSessionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for StaleSessionCleanupService.
 *
 * Validates: Requirements 4.2, 4.5, 4.6
 */
@ExtendWith(MockitoExtension.class)
class StaleSessionCleanupServiceTest {

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Nested
    @DisplayName("Default threshold behavior")
    class DefaultThreshold {

        @Test
        @DisplayName("Default threshold of 24 hours produces correct cutoff time")
        void defaultThresholdProducesCorrectCutoff() {
            // Arrange: create service with default 24-hour threshold
            StaleSessionCleanupService service = new StaleSessionCleanupService(
                    chatSessionRepository, 24L);
            when(chatSessionRepository.expireStaleSessions(any(LocalDateTime.class))).thenReturn(0);

            // Act
            LocalDateTime before = LocalDateTime.now().minusHours(24);
            service.cleanupStaleSessions();
            LocalDateTime after = LocalDateTime.now().minusHours(24);

            // Assert: the cutoff passed to the repository is within the expected range
            ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(chatSessionRepository).expireStaleSessions(cutoffCaptor.capture());

            LocalDateTime capturedCutoff = cutoffCaptor.getValue();
            // The cutoff should be between 'before' and 'after' (both are now-24h, bracketing the call)
            assertThat(capturedCutoff).isAfterOrEqualTo(before);
            assertThat(capturedCutoff).isBeforeOrEqualTo(after);
        }

        @Test
        @DisplayName("Cutoff is approximately 24 hours before current time")
        void cutoffIsApproximately24HoursBeforeNow() {
            StaleSessionCleanupService service = new StaleSessionCleanupService(
                    chatSessionRepository, 24L);
            when(chatSessionRepository.expireStaleSessions(any(LocalDateTime.class))).thenReturn(5);

            LocalDateTime now = LocalDateTime.now();
            service.cleanupStaleSessions();

            ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(chatSessionRepository).expireStaleSessions(cutoffCaptor.capture());

            LocalDateTime capturedCutoff = cutoffCaptor.getValue();
            Duration diff = Duration.between(capturedCutoff, now);
            // Should be approximately 24 hours (allow 1 second tolerance for test execution)
            assertThat(diff.toHours()).isEqualTo(24L);
        }

        @Test
        @DisplayName("Custom threshold produces proportionally different cutoff")
        void customThresholdProducesCorrectCutoff() {
            // Use a 48-hour threshold
            StaleSessionCleanupService service = new StaleSessionCleanupService(
                    chatSessionRepository, 48L);
            when(chatSessionRepository.expireStaleSessions(any(LocalDateTime.class))).thenReturn(3);

            LocalDateTime now = LocalDateTime.now();
            service.cleanupStaleSessions();

            ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(chatSessionRepository).expireStaleSessions(cutoffCaptor.capture());

            LocalDateTime capturedCutoff = cutoffCaptor.getValue();
            Duration diff = Duration.between(capturedCutoff, now);
            assertThat(diff.toHours()).isEqualTo(48L);
        }
    }

    @Nested
    @DisplayName("Cleanup execution behavior")
    class CleanupExecution {

        @Test
        @DisplayName("cleanupStaleSessions calls repository expireStaleSessions exactly once")
        void cleanupCallsRepositoryOnce() {
            StaleSessionCleanupService service = new StaleSessionCleanupService(
                    chatSessionRepository, 24L);
            when(chatSessionRepository.expireStaleSessions(any(LocalDateTime.class))).thenReturn(0);

            service.cleanupStaleSessions();

            verify(chatSessionRepository, times(1)).expireStaleSessions(any(LocalDateTime.class));
        }

        @Test
        @DisplayName("cleanupStaleSessions does not throw when repository returns zero expired sessions")
        void cleanupHandlesZeroExpiredSessions() {
            StaleSessionCleanupService service = new StaleSessionCleanupService(
                    chatSessionRepository, 24L);
            when(chatSessionRepository.expireStaleSessions(any(LocalDateTime.class))).thenReturn(0);

            // Should not throw
            service.cleanupStaleSessions();

            verify(chatSessionRepository).expireStaleSessions(any(LocalDateTime.class));
        }
    }

    @Nested
    @DisplayName("Conditional property configuration")
    class ConditionalProperty {

        @Test
        @DisplayName("Service is annotated with @ConditionalOnProperty for chatbot.cleanup.enabled")
        void serviceHasConditionalOnPropertyAnnotation() {
            // Verify the class-level annotation exists and has the correct attributes.
            // This is a configuration test — the actual conditional bean creation
            // is tested by Spring's application context when chatbot.cleanup.enabled=false.
            var annotation = StaleSessionCleanupService.class
                    .getAnnotation(org.springframework.boot.autoconfigure.condition.ConditionalOnProperty.class);

            assertThat(annotation).isNotNull();
            assertThat(annotation.name()).containsExactly("chatbot.cleanup.enabled");
            assertThat(annotation.havingValue()).isEqualTo("true");
            assertThat(annotation.matchIfMissing()).isTrue();
        }

        @Test
        @DisplayName("matchIfMissing=true means bean IS created when property is absent (default enabled)")
        void matchIfMissingTrueMeansBeanCreatedByDefault() {
            // When chatbot.cleanup.enabled is not set at all, matchIfMissing=true
            // means the service bean WILL be created — cleanup is enabled by default.
            var annotation = StaleSessionCleanupService.class
                    .getAnnotation(org.springframework.boot.autoconfigure.condition.ConditionalOnProperty.class);

            assertThat(annotation.matchIfMissing())
                    .as("matchIfMissing should be true so the cleanup runs by default")
                    .isTrue();
        }

        @Test
        @DisplayName("Bean is NOT created when chatbot.cleanup.enabled=false (verified via annotation semantics)")
        void beanNotCreatedWhenDisabled() {
            // When chatbot.cleanup.enabled=false, the havingValue="true" does not match,
            // so Spring will NOT instantiate the bean. This test documents that behavior
            // by verifying the annotation's contract: havingValue must be "true" for creation.
            var annotation = StaleSessionCleanupService.class
                    .getAnnotation(org.springframework.boot.autoconfigure.condition.ConditionalOnProperty.class);

            assertThat(annotation.havingValue())
                    .as("havingValue is 'true', so setting the property to 'false' prevents bean creation")
                    .isEqualTo("true");
        }
    }
}

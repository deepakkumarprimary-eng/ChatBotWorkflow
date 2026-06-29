package com.xpressbees.chatbot.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.support.GenericApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GracefulShutdownListener covering shutdown initiation,
 * drain completion, and timeout behavior.
 *
 * Validates: Requirements 5.2, 5.3, 5.4
 */
@ExtendWith(MockitoExtension.class)
class GracefulShutdownListenerTest {

    @Mock
    private ExecutionTracker executionTracker;

    private ContextClosedEvent createContextClosedEvent() {
        return new ContextClosedEvent(new GenericApplicationContext());
    }

    @Nested
    @DisplayName("Shutdown initiation")
    class ShutdownInitiation {

        @Test
        @DisplayName("onApplicationEvent calls beginShutdown on ExecutionTracker")
        void callsBeginShutdown() {
            when(executionTracker.getActiveCount()).thenReturn(0);

            GracefulShutdownListener listener = new GracefulShutdownListener(executionTracker, 30);
            listener.onApplicationEvent(createContextClosedEvent());

            verify(executionTracker).beginShutdown();
        }

        @Test
        @DisplayName("Completes immediately when no active executions")
        void completesImmediatelyWhenNoActiveExecutions() {
            when(executionTracker.getActiveCount()).thenReturn(0);

            GracefulShutdownListener listener = new GracefulShutdownListener(executionTracker, 30);

            long start = System.currentTimeMillis();
            listener.onApplicationEvent(createContextClosedEvent());
            long elapsed = System.currentTimeMillis() - start;

            // Should complete in well under 1 second (no polling needed)
            assertThat(elapsed).isLessThan(1000);
            verify(executionTracker).beginShutdown();
        }
    }

    @Nested
    @DisplayName("Drain completion")
    class DrainCompletion {

        @Test
        @DisplayName("Waits and completes when active executions drain to zero")
        void waitsForExecutionsToDrain() {
            // Simulate: first call returns 2, second returns 1, third returns 0
            when(executionTracker.getActiveCount())
                    .thenReturn(2)
                    .thenReturn(1)
                    .thenReturn(0);

            GracefulShutdownListener listener = new GracefulShutdownListener(executionTracker, 30);
            listener.onApplicationEvent(createContextClosedEvent());

            verify(executionTracker).beginShutdown();
            // getActiveCount should be called multiple times as it polls
            verify(executionTracker, atLeast(2)).getActiveCount();
        }
    }

    @Nested
    @DisplayName("Timeout behavior")
    class TimeoutBehavior {

        @Test
        @DisplayName("Times out when executions never drain - uses short timeout")
        void timesOutWhenExecutionsNeverDrain() {
            // Mock tracker always reports active executions
            when(executionTracker.getActiveCount()).thenReturn(5);

            // Use a very short timeout (1 second) so the test runs quickly
            GracefulShutdownListener listener = new GracefulShutdownListener(executionTracker, 1);

            long start = System.currentTimeMillis();
            listener.onApplicationEvent(createContextClosedEvent());
            long elapsed = System.currentTimeMillis() - start;

            verify(executionTracker).beginShutdown();
            // Should have taken approximately 1 second (the timeout)
            assertThat(elapsed).isBetween(900L, 2500L);
            // getActiveCount was polled multiple times during the wait
            verify(executionTracker, atLeast(2)).getActiveCount();
        }

        @Test
        @DisplayName("Times out with 2 second timeout when executions remain")
        void timesOutWithTwoSecondTimeout() {
            when(executionTracker.getActiveCount()).thenReturn(3);

            GracefulShutdownListener listener = new GracefulShutdownListener(executionTracker, 2);

            long start = System.currentTimeMillis();
            listener.onApplicationEvent(createContextClosedEvent());
            long elapsed = System.currentTimeMillis() - start;

            verify(executionTracker).beginShutdown();
            // Should have taken approximately 2 seconds
            assertThat(elapsed).isBetween(1800L, 3500L);
        }
    }

    @Nested
    @DisplayName("Integration with real ExecutionTracker")
    class RealTrackerIntegration {

        @Test
        @DisplayName("Works with real ExecutionTracker when no active executions")
        void worksWithRealTrackerNoActive() {
            ExecutionTracker realTracker = new ExecutionTracker();
            GracefulShutdownListener listener = new GracefulShutdownListener(realTracker, 5);

            listener.onApplicationEvent(createContextClosedEvent());

            assertThat(realTracker.isShuttingDown()).isTrue();
            assertThat(realTracker.getActiveCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("Works with real ExecutionTracker draining from background thread")
        void worksWithRealTrackerDraining() throws InterruptedException {
            ExecutionTracker realTracker = new ExecutionTracker();
            realTracker.tryStart();
            realTracker.tryStart();

            // Complete executions from another thread after a short delay
            Thread drainThread = new Thread(() -> {
                try {
                    Thread.sleep(300);
                    realTracker.complete();
                    Thread.sleep(300);
                    realTracker.complete();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            drainThread.start();

            GracefulShutdownListener listener = new GracefulShutdownListener(realTracker, 5);

            long start = System.currentTimeMillis();
            listener.onApplicationEvent(createContextClosedEvent());
            long elapsed = System.currentTimeMillis() - start;

            drainThread.join(5000);

            assertThat(realTracker.isShuttingDown()).isTrue();
            assertThat(realTracker.getActiveCount()).isEqualTo(0);
            // Should complete in under 2 seconds (executions drain within ~600ms)
            assertThat(elapsed).isLessThan(2000);
        }
    }
}

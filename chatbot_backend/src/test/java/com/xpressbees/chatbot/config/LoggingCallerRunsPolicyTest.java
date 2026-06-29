package com.xpressbees.chatbot.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for LoggingCallerRunsPolicy WARN log emission.
 *
 * <p>Validates: Requirement 5.2</p>
 */
class LoggingCallerRunsPolicyTest {

    private ListAppender<ILoggingEvent> logAppender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(LoggingCallerRunsPolicy.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logAppender);
        logAppender.stop();
    }

    @Test
    void rejectedExecutionLogsWarnWithActiveCountAndQueueSize() {
        LoggingCallerRunsPolicy policy = new LoggingCallerRunsPolicy();

        // Create a tiny pool that will be saturated
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1)
        );
        executor.setRejectedExecutionHandler(policy);

        try {
            // Fill the pool and queue
            executor.execute(() -> {
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            });
            executor.execute(() -> {
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            });

            // This should trigger CallerRunsPolicy and the WARN log
            Runnable rejectedTask = () -> {};
            policy.rejectedExecution(rejectedTask, executor);

            assertThat(logAppender.list).isNotEmpty();
            ILoggingEvent logEvent = logAppender.list.get(logAppender.list.size() - 1);
            assertThat(logEvent.getLevel()).isEqualTo(Level.WARN);
            assertThat(logEvent.getFormattedMessage()).contains("WebSocket thread pool saturated!");
            assertThat(logEvent.getFormattedMessage()).contains("active=");
            assertThat(logEvent.getFormattedMessage()).contains("queueSize=");
            assertThat(logEvent.getFormattedMessage()).contains("executing on caller thread");
        } finally {
            executor.shutdownNow();
        }
    }
}

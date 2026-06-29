package com.xpressbees.chatbot.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test verifying thread pool properties bind correctly
 * and executor beans are created with the expected configuration.
 *
 * <p>Validates: Requirements 1.1, 2.1, 2.2, 2.3, 4.1</p>
 */
@ExtendWith(SpringExtension.class)
@EnableConfigurationProperties(WebSocketResilienceProperties.class)
@TestPropertySource(properties = {
        "chatbot.websocket.inbound-pool-core-size=2",
        "chatbot.websocket.inbound-pool-max-size=4",
        "chatbot.websocket.inbound-pool-queue-capacity=5",
        "chatbot.websocket.outbound-pool-core-size=2",
        "chatbot.websocket.outbound-pool-max-size=4",
        "chatbot.websocket.outbound-pool-queue-capacity=5",
        "chatbot.websocket.max-connections=100",
        "chatbot.websocket.inactivity-timeout-minutes=5",
        "chatbot.websocket.heartbeat-interval-ms=5000",
        "chatbot.websocket.send-buffer-size=10",
        "chatbot.websocket.buffer-drain-timeout-seconds=5"
})
class WebSocketThreadPoolIntegrationTest {

    @Configuration
    @EnableConfigurationProperties(WebSocketResilienceProperties.class)
    static class TestConfig {
        @Bean
        public ThreadPoolTaskExecutor inboundExecutor(WebSocketResilienceProperties props) {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(props.getInboundPoolCoreSize());
            executor.setMaxPoolSize(props.getInboundPoolMaxSize());
            executor.setQueueCapacity(props.getInboundPoolQueueCapacity());
            executor.setThreadNamePrefix("ws-inbound-");
            executor.setRejectedExecutionHandler(new LoggingCallerRunsPolicy());
            executor.setWaitForTasksToCompleteOnShutdown(true);
            executor.setAwaitTerminationSeconds(30);
            executor.initialize();
            return executor;
        }

        @Bean
        public ThreadPoolTaskExecutor outboundExecutor(WebSocketResilienceProperties props) {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(props.getOutboundPoolCoreSize());
            executor.setMaxPoolSize(props.getOutboundPoolMaxSize());
            executor.setQueueCapacity(props.getOutboundPoolQueueCapacity());
            executor.setThreadNamePrefix("ws-outbound-");
            executor.setRejectedExecutionHandler(new LoggingCallerRunsPolicy());
            executor.setWaitForTasksToCompleteOnShutdown(true);
            executor.setAwaitTerminationSeconds(30);
            executor.initialize();
            return executor;
        }
    }

    @Autowired
    private WebSocketResilienceProperties resilienceProperties;

    @Autowired
    private ThreadPoolTaskExecutor inboundExecutor;

    @Autowired
    private ThreadPoolTaskExecutor outboundExecutor;

    @Test
    void inboundPropertiesBindFromTestProfile() {
        assertThat(resilienceProperties.getInboundPoolCoreSize()).isEqualTo(2);
        assertThat(resilienceProperties.getInboundPoolMaxSize()).isEqualTo(4);
        assertThat(resilienceProperties.getInboundPoolQueueCapacity()).isEqualTo(5);
    }

    @Test
    void outboundPropertiesBindFromTestProfile() {
        assertThat(resilienceProperties.getOutboundPoolCoreSize()).isEqualTo(2);
        assertThat(resilienceProperties.getOutboundPoolMaxSize()).isEqualTo(4);
        assertThat(resilienceProperties.getOutboundPoolQueueCapacity()).isEqualTo(5);
    }

    @Test
    void inboundExecutorConfiguredCorrectly() {
        assertThat(inboundExecutor.getCorePoolSize()).isEqualTo(2);
        assertThat(inboundExecutor.getMaxPoolSize()).isEqualTo(4);
        assertThat(inboundExecutor.getThreadNamePrefix()).isEqualTo("ws-inbound-");
    }

    @Test
    void outboundExecutorConfiguredCorrectly() {
        assertThat(outboundExecutor.getCorePoolSize()).isEqualTo(2);
        assertThat(outboundExecutor.getMaxPoolSize()).isEqualTo(4);
        assertThat(outboundExecutor.getThreadNamePrefix()).isEqualTo("ws-outbound-");
    }
}

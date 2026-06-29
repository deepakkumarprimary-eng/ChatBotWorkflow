package com.xpressbees.chatbot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);

    private final String[] allowedOrigins;
    private final WebSocketAuthInterceptor webSocketAuthInterceptor;
    private final ConnectionLimitInterceptor connectionLimitInterceptor;
    private final WebSocketResilienceProperties resilienceProperties;

    public WebSocketConfig(@Value("${cors.allowed-origins:http://localhost:3000}") String[] allowedOrigins,
                           WebSocketAuthInterceptor webSocketAuthInterceptor,
                           ConnectionLimitInterceptor connectionLimitInterceptor,
                           WebSocketResilienceProperties resilienceProperties) {
        this.allowedOrigins = allowedOrigins;
        this.webSocketAuthInterceptor = webSocketAuthInterceptor;
        this.connectionLimitInterceptor = connectionLimitInterceptor;
        this.resilienceProperties = resilienceProperties;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        long heartbeatIntervalMs = resilienceProperties.getHeartbeatIntervalMs();
        config.enableSimpleBroker("/topic")
              .setHeartbeatValue(new long[]{heartbeatIntervalMs, heartbeatIntervalMs})
              .setTaskScheduler(heartbeatScheduler());
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins)
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketAuthInterceptor, connectionLimitInterceptor);
        registration.taskExecutor(inboundExecutor());
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.taskExecutor(outboundExecutor());
    }

    @Bean
    public ThreadPoolTaskExecutor inboundExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(resilienceProperties.getInboundPoolCoreSize());
        executor.setMaxPoolSize(resilienceProperties.getInboundPoolMaxSize());
        executor.setQueueCapacity(resilienceProperties.getInboundPoolQueueCapacity());
        executor.setThreadNamePrefix("ws-inbound-");
        executor.setRejectedExecutionHandler(new LoggingCallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();

        log.info("WebSocket inbound thread pool configured: coreSize={}, maxSize={}, queueCapacity={}, prefix=ws-inbound-",
                resilienceProperties.getInboundPoolCoreSize(),
                resilienceProperties.getInboundPoolMaxSize(),
                resilienceProperties.getInboundPoolQueueCapacity());

        return executor;
    }

    @Bean
    public ThreadPoolTaskExecutor outboundExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(resilienceProperties.getOutboundPoolCoreSize());
        executor.setMaxPoolSize(resilienceProperties.getOutboundPoolMaxSize());
        executor.setQueueCapacity(resilienceProperties.getOutboundPoolQueueCapacity());
        executor.setThreadNamePrefix("ws-outbound-");
        executor.setRejectedExecutionHandler(new LoggingCallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();

        log.info("WebSocket outbound thread pool configured: coreSize={}, maxSize={}, queueCapacity={}, prefix=ws-outbound-",
                resilienceProperties.getOutboundPoolCoreSize(),
                resilienceProperties.getOutboundPoolMaxSize(),
                resilienceProperties.getOutboundPoolQueueCapacity());

        return executor;
    }

    @Bean
    public ThreadPoolTaskScheduler heartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }
}

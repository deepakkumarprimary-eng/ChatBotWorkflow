package com.xpressbees.chatbot.config;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "chatbot.websocket")
public class WebSocketResilienceProperties {

    @Min(1)
    private int maxConnections = 1000;

    @Min(1)
    private int inactivityTimeoutMinutes = 30;

    @Min(1000)
    private long heartbeatIntervalMs = 10000;

    @Min(1)
    private int sendBufferSize = 50;

    @Min(1)
    private int bufferDrainTimeoutSeconds = 30;
}

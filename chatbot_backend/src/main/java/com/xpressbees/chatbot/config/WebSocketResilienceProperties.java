package com.xpressbees.chatbot.config;

import jakarta.validation.constraints.AssertTrue;
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

    // Inbound channel thread pool configuration
    @Min(1)
    private int inboundPoolCoreSize = 10;

    @Min(1)
    private int inboundPoolMaxSize = 50;

    @Min(1)
    private int inboundPoolQueueCapacity = 200;

    // Outbound channel thread pool configuration
    @Min(1)
    private int outboundPoolCoreSize = 10;

    @Min(1)
    private int outboundPoolMaxSize = 50;

    @Min(1)
    private int outboundPoolQueueCapacity = 200;

    @AssertTrue(message = "inbound-pool-max-size must be >= inbound-pool-core-size")
    public boolean isInboundPoolSizesValid() {
        return inboundPoolMaxSize >= inboundPoolCoreSize;
    }

    @AssertTrue(message = "outbound-pool-max-size must be >= outbound-pool-core-size")
    public boolean isOutboundPoolSizesValid() {
        return outboundPoolMaxSize >= outboundPoolCoreSize;
    }
}

package com.xpressbees.chatbot.service;

import com.sun.net.httpserver.HttpServer;
import com.xpressbees.chatbot.dto.HttpExecutionResult;
import com.xpressbees.chatbot.entity.ApiConfig;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

// Feature: redis-caching-and-performance, Property 8: 4xx client errors are never retried

/**
 * Property-based test for HttpExecutor 4xx no-retry behavior.
 *
 * Validates: Requirements 5.5
 *
 * For any HTTP response with a 4xx status code (400-499), the HttpExecutor SHALL
 * return the error result immediately after the first attempt without performing
 * any retry or delay.
 *
 * This test complements HttpExecutorRetryPropertyTest.noRetryOn4xxErrors by using
 * random 4xx status codes across the full 400-499 range (rather than a fixed 400)
 * and a wider retry count range (0-5).
 */
class HttpExecutorNoRetryTest {

    @Property(tries = 100)
    void clientErrorsAreNeverRetried(
            @ForAll @IntRange(min = 400, max = 499) int statusCode,
            @ForAll @IntRange(min = 0, max = 5) int retryCount) throws IOException {
        // Feature: redis-caching-and-performance, Property 8: 4xx client errors are never retried
        // Validates: Requirements 5.5

        AtomicInteger requestCount = new AtomicInteger(0);

        // Start an embedded HTTP server that returns the random 4xx status code
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();

        server.createContext("/test", exchange -> {
            requestCount.incrementAndGet();
            String response = "Client Error " + statusCode;
            exchange.sendResponseHeaders(statusCode, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        });
        server.start();

        try {
            // Create a testable HttpExecutor that skips the retry sleep
            HttpExecutor executor = new HttpExecutor(1000, 10000) {
                @Override
                boolean sleepBeforeRetry(int attemptNumber) {
                    // No-op for fast tests
                    return true;
                }
            };

            // Configure ApiConfig with the test server URL and desired retryCount
            ApiConfig config = new ApiConfig();
            config.setMethod("GET");
            config.setTimeoutMs(5000);
            config.setRetryCount(retryCount);

            String url = "http://localhost:" + port + "/test";
            Map<String, String> headers = new HashMap<>();

            // Execute the request
            HttpExecutionResult result = executor.execute(config, url, headers, null);

            // Verify: the request failed
            assert !result.isSuccess() :
                    "Expected failure result for " + statusCode + " error. Got success.";

            // Verify: the result contains the correct status code
            assert result.getStatusCode() == statusCode :
                    "Expected status code " + statusCode + " but got " + result.getStatusCode();

            // Verify: only 1 attempt was made (no retries for 4xx)
            int actualAttempts = requestCount.get();
            assert actualAttempts == 1 :
                    "4xx errors (status=" + statusCode + ") should not be retried. " +
                    "With retryCount=" + retryCount + ", expected 1 attempt but got " + actualAttempts;

        } finally {
            server.stop(0);
        }
    }
}

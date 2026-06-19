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

/**
 * Property-based tests for HttpExecutor retry logic.
 * - Property 4: Retry Count Correctness
 *
 * Validates: Requirements 4.5
 */
class HttpExecutorRetryPropertyTest {

    // ========================================================================
    // Property 4: Retry Count Correctness
    // ========================================================================

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 4: Retry Count Correctness")
    void retryCountNMakesExactlyNPlusOneAttempts(
            @ForAll @IntRange(min = 0, max = 3) int retryCount) throws IOException {
        // Validates: Requirements 4.5
        // For any retryCount N and always-failing requests (5xx),
        // exactly N+1 total attempts are made.

        AtomicInteger requestCount = new AtomicInteger(0);

        // Start an embedded HTTP server that always returns 500
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();

        server.createContext("/test", exchange -> {
            requestCount.incrementAndGet();
            String response = "Internal Server Error";
            exchange.sendResponseHeaders(500, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        });
        server.start();

        try {
            // Create a testable HttpExecutor that skips the retry sleep
            HttpExecutor executor = new HttpExecutor() {
                @Override
                void sleepBeforeRetry() {
                    // No-op for fast tests
                }
            };

            // Configure ApiConfig with the test server URL and desired retryCount
            ApiConfig config = new ApiConfig();
            config.setMethod("GET");
            config.setTimeoutMs(5000);
            config.setRetryCount(retryCount);

            String url = "http://localhost:" + port + "/test";
            Map<String, String> headers = new HashMap<>();

            // Execute the request (will fail every time with 500)
            HttpExecutionResult result = executor.execute(config, url, headers, null);

            // Verify: the request failed
            assert !result.isSuccess() :
                    "Expected failure result for always-failing server. Got success.";

            // Verify: exactly retryCount + 1 total attempts were made
            int expectedAttempts = retryCount + 1;
            int actualAttempts = requestCount.get();
            assert actualAttempts == expectedAttempts :
                    "For retryCount=" + retryCount + ", expected " + expectedAttempts +
                    " attempts but got " + actualAttempts;
        } finally {
            server.stop(0);
        }
    }

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 4: Retry Count Correctness")
    void retryCountWithConnectionErrorMakesExactlyNPlusOneAttempts(
            @ForAll @IntRange(min = 0, max = 3) int retryCount) {
        // Validates: Requirements 4.5
        // For any retryCount N and always-failing requests (connection error),
        // exactly N+1 total attempts are made.

        // Create a testable HttpExecutor that skips the retry sleep
        HttpExecutor executor = new HttpExecutor() {
            @Override
            void sleepBeforeRetry() {
                // No-op for fast tests
            }
        };

        // Configure ApiConfig with an unreachable URL to trigger ResourceAccessException
        ApiConfig config = new ApiConfig();
        config.setMethod("GET");
        config.setTimeoutMs(500); // Short timeout to fail fast
        config.setRetryCount(retryCount);

        // Use a non-routable address that will cause connection timeout
        // Port 1 on localhost is typically not open
        String url = "http://localhost:1/unreachable";
        Map<String, String> headers = new HashMap<>();

        // Execute the request (will fail every time with connection error)
        HttpExecutionResult result = executor.execute(config, url, headers, null);

        // Verify: the request failed
        assert !result.isSuccess() :
                "Expected failure result for unreachable server. Got success.";

        // Verify: error message indicates connection error
        assert result.getErrorMessage() != null && result.getErrorMessage().contains("Connection error") :
                "Expected connection error message but got: " + result.getErrorMessage();
    }

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 4: Retry Count Correctness")
    void noRetryOn4xxErrors(
            @ForAll @IntRange(min = 0, max = 3) int retryCount) throws IOException {
        // Validates: Requirements 4.5
        // 4xx errors should NOT be retried — only 1 attempt total regardless of retryCount

        AtomicInteger requestCount = new AtomicInteger(0);

        // Start an embedded HTTP server that always returns 400
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();

        server.createContext("/test", exchange -> {
            requestCount.incrementAndGet();
            String response = "Bad Request";
            exchange.sendResponseHeaders(400, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        });
        server.start();

        try {
            HttpExecutor executor = new HttpExecutor() {
                @Override
                void sleepBeforeRetry() {
                    // No-op for fast tests
                }
            };

            ApiConfig config = new ApiConfig();
            config.setMethod("GET");
            config.setTimeoutMs(5000);
            config.setRetryCount(retryCount);

            String url = "http://localhost:" + port + "/test";
            Map<String, String> headers = new HashMap<>();

            HttpExecutionResult result = executor.execute(config, url, headers, null);

            // Verify: the request failed
            assert !result.isSuccess() :
                    "Expected failure result for 400 error. Got success.";

            // Verify: only 1 attempt was made (no retries for 4xx)
            int actualAttempts = requestCount.get();
            assert actualAttempts == 1 :
                    "4xx errors should not be retried. Expected 1 attempt but got " + actualAttempts;
        } finally {
            server.stop(0);
        }
    }
}

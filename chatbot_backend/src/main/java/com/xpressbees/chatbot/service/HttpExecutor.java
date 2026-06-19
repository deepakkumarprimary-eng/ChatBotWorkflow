package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.dto.HttpExecutionResult;
import com.xpressbees.chatbot.entity.ApiConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class HttpExecutor {

    private static final Logger log = LoggerFactory.getLogger(HttpExecutor.class);

    public HttpExecutionResult execute(ApiConfig config, String resolvedUrl,
                                       Map<String, String> resolvedHeaders,
                                       String resolvedBody) {
        int retryCount = (config.getRetryCount() != null) ? config.getRetryCount() : 1;
        int totalAttempts = retryCount + 1;
        HttpExecutionResult lastResult = null;

        for (int attempt = 1; attempt <= totalAttempts; attempt++) {
            try {
                RestClient restClient = buildRestClient(config.getTimeoutMs());

                String method = config.getMethod().toUpperCase();
                RestClient.RequestBodySpec requestSpec = restClient
                        .method(HttpMethod.valueOf(method))
                        .uri(resolvedUrl);

                // Apply resolved headers
                if (resolvedHeaders != null) {
                    resolvedHeaders.forEach(requestSpec::header);
                }

                // Send body for POST/PUT requests
                if (("POST".equals(method) || "PUT".equals(method)) && resolvedBody != null) {
                    requestSpec.contentType(MediaType.APPLICATION_JSON);
                    requestSpec.body(resolvedBody);
                }

                String responseBody = requestSpec.retrieve()
                        .body(String.class);

                return new HttpExecutionResult(true, 200, responseBody, null);

            } catch (HttpClientErrorException e) {
                // 4xx errors are NOT retryable - return immediately
                log.warn("HTTP client error calling {}: {} {}", resolvedUrl, e.getStatusCode().value(), e.getStatusText());
                return new HttpExecutionResult(false, e.getStatusCode().value(), null,
                        "HTTP client error: " + e.getStatusCode().value() + " " + e.getStatusText());

            } catch (HttpServerErrorException e) {
                // 5xx errors ARE retryable
                log.warn("HTTP server error calling {} (attempt {}/{}): {} {}",
                        resolvedUrl, attempt, totalAttempts, e.getStatusCode().value(), e.getStatusText());
                lastResult = new HttpExecutionResult(false, e.getStatusCode().value(), null,
                        "HTTP server error: " + e.getStatusCode().value() + " " + e.getStatusText());

                if (attempt < totalAttempts) {
                    sleepBeforeRetry();
                }

            } catch (ResourceAccessException e) {
                // Timeout/connection errors ARE retryable
                log.warn("Connection/timeout error calling {} (attempt {}/{}): {}",
                        resolvedUrl, attempt, totalAttempts, e.getMessage());
                lastResult = new HttpExecutionResult(false, 0, null,
                        "Connection error: " + e.getMessage());

                if (attempt < totalAttempts) {
                    sleepBeforeRetry();
                }

            } catch (Exception e) {
                // Unexpected errors are NOT retryable - return immediately
                log.error("Unexpected error calling {}: {}", resolvedUrl, e.getMessage(), e);
                return new HttpExecutionResult(false, 0, null,
                        "Unexpected error: " + e.getMessage());
            }
        }

        // All retries exhausted - return last failure result
        return lastResult;
    }

    void sleepBeforeRetry() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("Retry sleep interrupted");
        }
    }

    private RestClient buildRestClient(Integer timeoutMs) {
        int timeout = (timeoutMs != null) ? timeoutMs : 5000;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);

        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }
}

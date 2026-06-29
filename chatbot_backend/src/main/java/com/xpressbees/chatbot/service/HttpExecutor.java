package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.dto.HttpExecutionResult;
import com.xpressbees.chatbot.dto.UrlValidationResult;
import com.xpressbees.chatbot.entity.ApiConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

    private final RestClientPool restClientPool;
    private final UrlValidator urlValidator;
    private final long baseDelayMs;
    private final long maxDelayMs;

    @org.springframework.beans.factory.annotation.Autowired
    public HttpExecutor(
            RestClientPool restClientPool,
            UrlValidator urlValidator,
            @Value("${http.retry.base-delay-ms:1000}") long baseDelayMs,
            @Value("${http.retry.max-delay-ms:10000}") long maxDelayMs) {
        this.restClientPool = restClientPool;
        this.urlValidator = urlValidator;
        this.baseDelayMs = baseDelayMs;
        this.maxDelayMs = maxDelayMs;
    }

    /**
     * Test-only constructor that does not require a RestClientPool or UrlValidator.
     * Falls back to creating a SimpleClientHttpRequestFactory-based RestClient per request.
     */
    HttpExecutor(long baseDelayMs, long maxDelayMs) {
        this.restClientPool = null;
        this.urlValidator = null;
        this.baseDelayMs = baseDelayMs;
        this.maxDelayMs = maxDelayMs;
    }

    public HttpExecutionResult execute(ApiConfig config, String resolvedUrl,
                                       Map<String, String> resolvedHeaders,
                                       String resolvedBody) {
        // Defense in depth: validate URL before execution regardless of upstream validation
        if (urlValidator != null) {
            UrlValidationResult urlValidation = urlValidator.validate(resolvedUrl);
            if (!urlValidation.isAllowed()) {
                log.warn("SSRF protection blocked URL in HttpExecutor: {} - reason: {}", resolvedUrl, urlValidation.reason());
                return new HttpExecutionResult(false, 0, null,
                        "SSRF protection: URL blocked by security policy - " + urlValidation.reason());
            }
        }

        int retryCount = (config.getRetryCount() != null) ? config.getRetryCount() : 1;
        int totalAttempts = retryCount + 1;
        HttpExecutionResult lastResult = null;

        for (int attempt = 1; attempt <= totalAttempts; attempt++) {
            try {
                RestClient restClient = getRestClient(config.getTimeoutMs());

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
                    if (!sleepBeforeRetry(attempt)) {
                        // Thread was interrupted — stop retrying, return last failure
                        return lastResult;
                    }
                }

            } catch (ResourceAccessException e) {
                // Timeout/connection errors ARE retryable
                log.warn("Connection/timeout error calling {} (attempt {}/{}): {}",
                        resolvedUrl, attempt, totalAttempts, e.getMessage());
                lastResult = new HttpExecutionResult(false, 0, null,
                        "Connection error: " + e.getMessage());

                if (attempt < totalAttempts) {
                    if (!sleepBeforeRetry(attempt)) {
                        // Thread was interrupted — stop retrying, return last failure
                        return lastResult;
                    }
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

    /**
     * Computes the exponential backoff delay for the given attempt number.
     * attemptNumber is 1-based (1st retry, 2nd retry, etc.).
     * Formula: min(baseDelay * 2^(attemptNumber-1), maxDelay)
     *
     * Handles overflow safely: if the shift exponent is >= 63 or the multiplication
     * would overflow, the result is capped at maxDelayMs.
     *
     * @param attemptNumber the 1-based retry attempt number
     * @return the delay in milliseconds, capped at maxDelayMs
     */
    long computeDelay(int attemptNumber) {
        int exponent = attemptNumber - 1;
        // Guard against overflow: if exponent >= 63, the shift itself overflows long
        if (exponent >= Long.SIZE - 1) {
            return maxDelayMs;
        }
        long shifted = 1L << exponent;
        // Check for multiplication overflow before multiplying
        if (shifted > maxDelayMs / baseDelayMs) {
            return maxDelayMs;
        }
        long delay = baseDelayMs * shifted;
        return Math.min(delay, maxDelayMs);
    }

    /**
     * Sleeps for an exponentially increasing duration based on the attempt number.
     * On InterruptedException, restores the interrupt flag and signals the caller
     * to stop retrying by returning false.
     *
     * @param attemptNumber the 1-based retry attempt number
     * @return true if sleep completed normally, false if interrupted
     */
    boolean sleepBeforeRetry(int attemptNumber) {
        long delay = computeDelay(attemptNumber);
        try {
            Thread.sleep(delay);
            return true;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("Retry sleep interrupted at attempt {}", attemptNumber);
            return false;
        }
    }

    private RestClient getRestClient(Integer timeoutMs) {
        int timeout = (timeoutMs != null) ? timeoutMs : 5000;

        if (restClientPool != null) {
            return restClientPool.getClient(timeout);
        }

        // Fallback for tests: create a simple RestClient per request
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);

        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }
}

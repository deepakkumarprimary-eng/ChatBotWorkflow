package com.xpressbees.chatbot.service;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe pool of RestClient instances keyed by timeout value (milliseconds).
 * Reuses existing clients for the same timeout configuration, avoiding per-request instantiation.
 * Uses Apache HttpClient 5 with PoolingHttpClientConnectionManager for TCP connection reuse.
 */
@Component
public class RestClientPool {

    private static final Logger log = LoggerFactory.getLogger(RestClientPool.class);

    private final ConcurrentHashMap<Integer, RestClient> pool = new ConcurrentHashMap<>();
    private final PoolingHttpClientConnectionManager connectionManager;
    private final int maxPoolSize;

    public RestClientPool(
            @Value("${http.client.pool.max-size:20}") int maxPoolSize,
            @Value("${http.client.pool.max-connections-per-route:20}") int maxConnectionsPerRoute,
            @Value("${http.client.pool.max-connections-total:100}") int maxConnectionsTotal) {
        this.maxPoolSize = maxPoolSize;
        this.connectionManager = new PoolingHttpClientConnectionManager();
        this.connectionManager.setDefaultMaxPerRoute(maxConnectionsPerRoute);
        this.connectionManager.setMaxTotal(maxConnectionsTotal);
    }

    /**
     * Returns a RestClient configured with the given timeout.
     * Reuses existing instances; creates new ones if pool has capacity.
     * If pool is at max capacity and timeout not found, returns the
     * closest available timeout client (minor timeout deviation acceptable).
     *
     * @param timeoutMs the desired timeout in milliseconds
     * @return a RestClient instance configured with the specified (or closest) timeout
     */
    public RestClient getClient(int timeoutMs) {
        // Fast path: exact match in the pool
        RestClient existing = pool.get(timeoutMs);
        if (existing != null) {
            return existing;
        }

        // Check if pool has capacity to add a new entry
        if (pool.size() < maxPoolSize) {
            // Use computeIfAbsent for thread-safe insertion
            return pool.computeIfAbsent(timeoutMs, this::buildRestClient);
        }

        // Pool is at max capacity — find closest existing timeout
        log.info("RestClient pool at max size ({}), returning closest-timeout client for {}ms",
                maxPoolSize, timeoutMs);
        return findClosestClient(timeoutMs);
    }

    private RestClient findClosestClient(int targetTimeoutMs) {
        int closestKey = -1;
        int minDiff = Integer.MAX_VALUE;

        for (Integer key : pool.keySet()) {
            int diff = Math.abs(key - targetTimeoutMs);
            if (diff < minDiff) {
                minDiff = diff;
                closestKey = key;
            }
        }

        if (closestKey == -1) {
            // Pool is empty (should not happen if maxPoolSize > 0), build one
            return buildRestClient(targetTimeoutMs);
        }

        return pool.get(closestKey);
    }

    private RestClient buildRestClient(int timeoutMs) {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(timeoutMs))
                .setResponseTimeout(Timeout.ofMilliseconds(timeoutMs))
                .build();

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setConnectionManagerShared(true)
                .setDefaultRequestConfig(requestConfig)
                .build();

        HttpComponentsClientHttpRequestFactory requestFactory =
                new HttpComponentsClientHttpRequestFactory(httpClient);

        log.debug("Created new RestClient for timeout {}ms (pool size: {})", timeoutMs, pool.size() + 1);

        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Returns the current number of distinct timeout configurations in the pool.
     * Useful for monitoring and testing.
     */
    public int size() {
        return pool.size();
    }

    /**
     * Returns the configured maximum pool size.
     * Useful for testing.
     */
    public int getMaxPoolSize() {
        return maxPoolSize;
    }
}

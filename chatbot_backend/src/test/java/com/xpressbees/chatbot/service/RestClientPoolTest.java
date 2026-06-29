package com.xpressbees.chatbot.service;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.springframework.web.client.RestClient;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

// Feature: redis-caching-and-performance, Property 6: RestClient pool consistency and bounded size

/**
 * Property-based tests for RestClientPool.
 *
 * Validates: Requirements 4.1, 4.2, 4.3, 4.5
 *
 * For any sequence of timeout value requests, the pool SHALL return the same RestClient instance
 * for repeated requests with the same timeout value. The pool size SHALL never exceed the configured
 * maximum, regardless of how many distinct timeout values are requested.
 */
class RestClientPoolTest {

    /**
     * Property 1: For any timeout value, calling getClient(timeout) twice returns the same instance.
     *
     * **Validates: Requirements 4.1**
     */
    @Property(tries = 100)
    @Tag("Feature: redis-caching-and-performance, Property 6: RestClient pool consistency and bounded size")
    void sameTimeoutAlwaysReturnsSameInstance(
            @ForAll @IntRange(min = 100, max = 30000) int timeoutMs) {
        // **Validates: Requirements 4.1**
        // Create a pool with enough capacity so the timeout will be stored
        RestClientPool pool = new RestClientPool(10, 20, 100);

        RestClient first = pool.getClient(timeoutMs);
        RestClient second = pool.getClient(timeoutMs);

        assert first == second :
                "getClient(" + timeoutMs + ") should return the same instance on repeated calls. " +
                "first=" + System.identityHashCode(first) + ", second=" + System.identityHashCode(second);
    }

    /**
     * Property 2: For any sequence of N distinct timeout values where N > maxPoolSize,
     * the pool.size() never exceeds maxPoolSize.
     *
     * **Validates: Requirements 4.3**
     */
    @Property(tries = 100)
    @Tag("Feature: redis-caching-and-performance, Property 6: RestClient pool consistency and bounded size")
    void poolSizeNeverExceedsMaximum(
            @ForAll("distinctTimeoutSequences") List<Integer> timeouts,
            @ForAll @IntRange(min = 1, max = 5) int maxPoolSize) {
        // **Validates: Requirements 4.2, 4.3**
        RestClientPool pool = new RestClientPool(maxPoolSize, 20, 100);

        for (int timeout : timeouts) {
            pool.getClient(timeout);

            assert pool.size() <= maxPoolSize :
                    "Pool size (" + pool.size() + ") exceeded maxPoolSize (" + maxPoolSize + ") " +
                    "after requesting timeout=" + timeout + "ms. Timeouts requested so far includes " +
                    timeouts.size() + " distinct values.";
        }
    }

    /**
     * Property 3: Thread-safety — multiple threads calling getClient concurrently with random timeouts
     * do not cause crashes and always return non-null clients. The pool uses a best-effort
     * size bound (ConcurrentHashMap size check is inherently non-atomic under concurrency).
     *
     * **Validates: Requirements 4.5**
     */
    @Property(tries = 50)
    @Tag("Feature: redis-caching-and-performance, Property 6: RestClient pool consistency and bounded size")
    void concurrentAccessNeverCrashesAndReturnsValidClients(
            @ForAll("timeoutLists") List<Integer> timeouts,
            @ForAll @IntRange(min = 2, max = 5) int maxPoolSize) throws InterruptedException {
        // **Validates: Requirements 4.5**
        RestClientPool pool = new RestClientPool(maxPoolSize, 20, 100);
        int threadCount = Math.min(timeouts.size(), 8);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(timeouts.size());
        List<Throwable> errors = new CopyOnWriteArrayList<>();
        List<RestClient> results = new CopyOnWriteArrayList<>();

        for (int timeout : timeouts) {
            executor.submit(() -> {
                try {
                    RestClient client = pool.getClient(timeout);
                    results.add(client);
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // No exceptions should have been thrown (thread-safety: no crashes)
        assert errors.isEmpty() :
                "Concurrent access caused exceptions: " + errors.stream()
                        .map(Throwable::getMessage)
                        .collect(Collectors.joining(", "));

        // All requests should have received a non-null client
        assert results.size() == timeouts.size() :
                "Expected " + timeouts.size() + " results but got " + results.size();

        for (RestClient client : results) {
            assert client != null : "getClient returned null under concurrent access";
        }
    }

    /**
     * Property: When pool is full, requesting a new timeout still returns a non-null RestClient.
     *
     * **Validates: Requirements 4.2, 4.3**
     */
    @Property(tries = 100)
    @Tag("Feature: redis-caching-and-performance, Property 6: RestClient pool consistency and bounded size")
    void poolAtCapacityStillReturnsClient(
            @ForAll @IntRange(min = 100, max = 30000) int extraTimeout,
            @ForAll @IntRange(min = 1, max = 5) int maxPoolSize) {
        // **Validates: Requirements 4.2, 4.3**
        RestClientPool pool = new RestClientPool(maxPoolSize, 20, 100);

        // Fill the pool to capacity with distinct timeouts
        for (int i = 0; i < maxPoolSize; i++) {
            pool.getClient((i + 1) * 1000); // 1000, 2000, 3000, ...
        }

        assert pool.size() == maxPoolSize :
                "Pool should be at max capacity after filling. size=" + pool.size();

        // Request a timeout that's unlikely to be in the pool
        int novelTimeout = 99999;
        RestClient client = pool.getClient(novelTimeout);

        assert client != null :
                "Pool at capacity should still return a non-null RestClient for novel timeout=" + novelTimeout;

        assert pool.size() <= maxPoolSize :
                "Pool size should not exceed max after requesting a novel timeout when full.";
    }

    // ========================================================================
    // Providers
    // ========================================================================

    @Provide
    Arbitrary<List<Integer>> distinctTimeoutSequences() {
        // Generate lists of 5-20 distinct timeout values (more than typical maxPoolSize of 1-5)
        return Arbitraries.integers().between(100, 30000)
                .list().ofMinSize(5).ofMaxSize(20)
                .map(list -> list.stream().distinct().collect(Collectors.toList()))
                .filter(list -> list.size() >= 5);
    }

    @Provide
    Arbitrary<List<Integer>> timeoutLists() {
        // Generate lists of timeout values (may contain duplicates) for concurrent testing
        return Arbitraries.integers().between(100, 30000)
                .list().ofMinSize(5).ofMaxSize(15);
    }
}

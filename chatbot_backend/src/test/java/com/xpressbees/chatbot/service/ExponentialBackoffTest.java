package com.xpressbees.chatbot.service;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

// Feature: redis-caching-and-performance, Property 7: Exponential backoff delay formula

/**
 * Property-based tests for exponential backoff delay computation in HttpExecutor.
 *
 * **Validates: Requirements 5.1, 5.2, 5.3**
 *
 * For any attempt number n (where n >= 1), the computed retry delay SHALL equal
 * min(1000 * 2^(n-1), 10000) milliseconds. The delay SHALL never be negative
 * and SHALL never exceed 10000ms.
 */
class ExponentialBackoffTest {

    private static final long BASE_DELAY = 1000L;
    private static final long MAX_DELAY = 10000L;

    /**
     * Property: For any attempt n >= 1, computeDelay(n) == min(1000 * 2^(n-1), 10000).
     * For large attempt numbers where the shift would overflow, the result is capped at maxDelay.
     *
     * **Validates: Requirements 5.1, 5.2**
     */
    @Property(tries = 100)
    @Tag("Feature: redis-caching-and-performance, Property 7: Exponential backoff delay formula")
    void delayMatchesExponentialFormula(
            @ForAll @IntRange(min = 1, max = 100) int attemptNumber) {
        // **Validates: Requirements 5.1, 5.2**
        HttpExecutor executor = new HttpExecutor(BASE_DELAY, MAX_DELAY);

        long actual = executor.computeDelay(attemptNumber);
        long expected = computeExpectedDelay(attemptNumber);

        assert actual == expected :
                "For attempt " + attemptNumber + ": expected delay=" + expected +
                "ms but got " + actual + "ms. Formula: min(1000 * 2^(" +
                attemptNumber + "-1), 10000)";
    }

    /**
     * Property: For any attempt n >= 1, the delay is never negative.
     *
     * **Validates: Requirements 5.1**
     */
    @Property(tries = 100)
    @Tag("Feature: redis-caching-and-performance, Property 7: Exponential backoff delay formula")
    void delayIsNeverNegative(
            @ForAll @IntRange(min = 1, max = 100) int attemptNumber) {
        // **Validates: Requirements 5.1**
        HttpExecutor executor = new HttpExecutor(BASE_DELAY, MAX_DELAY);

        long delay = executor.computeDelay(attemptNumber);

        assert delay >= 0 :
                "Delay must never be negative. For attempt " + attemptNumber +
                ", got delay=" + delay + "ms";
    }

    /**
     * Property: For any attempt n >= 1, the delay never exceeds maxDelay (10000ms).
     *
     * **Validates: Requirements 5.3**
     */
    @Property(tries = 100)
    @Tag("Feature: redis-caching-and-performance, Property 7: Exponential backoff delay formula")
    void delayNeverExceedsMaximum(
            @ForAll @IntRange(min = 1, max = 100) int attemptNumber) {
        // **Validates: Requirements 5.3**
        HttpExecutor executor = new HttpExecutor(BASE_DELAY, MAX_DELAY);

        long delay = executor.computeDelay(attemptNumber);

        assert delay <= MAX_DELAY :
                "Delay must never exceed max (" + MAX_DELAY + "ms). For attempt " +
                attemptNumber + ", got delay=" + delay + "ms";
    }

    /**
     * Property: For any two consecutive attempts n and n+1 where delay(n) < maxDelay,
     * delay(n+1) == 2 * delay(n) (doubling property).
     *
     * **Validates: Requirements 5.2**
     */
    @Property(tries = 100)
    @Tag("Feature: redis-caching-and-performance, Property 7: Exponential backoff delay formula")
    void delayDoublesForConsecutiveAttemptsBeforeCap(
            @ForAll @IntRange(min = 1, max = 99) int attemptNumber) {
        // **Validates: Requirements 5.2**
        HttpExecutor executor = new HttpExecutor(BASE_DELAY, MAX_DELAY);

        long delayN = executor.computeDelay(attemptNumber);
        long delayNPlus1 = executor.computeDelay(attemptNumber + 1);

        if (delayN < MAX_DELAY) {
            long expectedNext = Math.min(2 * delayN, MAX_DELAY);
            assert delayNPlus1 == expectedNext :
                    "For attempts " + attemptNumber + " and " + (attemptNumber + 1) +
                    ": when delay(" + attemptNumber + ")=" + delayN + "ms < maxDelay, " +
                    "expected delay(" + (attemptNumber + 1) + ")=" + expectedNext +
                    "ms but got " + delayNPlus1 + "ms";
        }
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    /**
     * Reference implementation of the expected delay formula,
     * handling overflow safely (same as the requirement states).
     */
    private long computeExpectedDelay(int attemptNumber) {
        int exponent = attemptNumber - 1;
        if (exponent >= Long.SIZE - 1) {
            return MAX_DELAY;
        }
        long shifted = 1L << exponent;
        if (shifted > MAX_DELAY / BASE_DELAY) {
            return MAX_DELAY;
        }
        long delay = BASE_DELAY * shifted;
        return Math.min(delay, MAX_DELAY);
    }
}

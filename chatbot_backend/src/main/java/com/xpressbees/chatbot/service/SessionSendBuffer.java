package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.dto.ChatResponse;

import java.time.Instant;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Per-session send buffer that holds outbound messages destined for a client.
 * Provides back-pressure signaling via pause/resume state tracking.
 * <p>
 * This is NOT a Spring bean — it is created per-session by the BufferedMessageSender.
 */
public class SessionSendBuffer {

    private final LinkedBlockingQueue<ChatResponse> queue;
    private final int maxSize;
    private volatile boolean paused;
    private volatile Instant pausedSince;

    public SessionSendBuffer(int maxSize) {
        if (maxSize < 1) {
            throw new IllegalArgumentException("maxSize must be at least 1");
        }
        this.queue = new LinkedBlockingQueue<>(maxSize);
        this.maxSize = maxSize;
        this.paused = false;
        this.pausedSince = null;
    }

    /**
     * Offers a message to the buffer (non-blocking).
     *
     * @param message the ChatResponse to enqueue
     * @return true if the message was added, false if the buffer is full
     */
    public boolean offer(ChatResponse message) {
        return queue.offer(message);
    }

    /**
     * Retrieves and removes the head of the buffer.
     *
     * @return the head ChatResponse, or null if the buffer is empty
     */
    public ChatResponse poll() {
        return queue.poll();
    }

    /**
     * Returns true if the buffer has reached its configured maximum size.
     */
    public boolean isFull() {
        return queue.size() >= maxSize;
    }

    /**
     * Returns true if the buffer contains no messages.
     */
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    /**
     * Returns the current number of messages in the buffer.
     */
    public int size() {
        return queue.size();
    }

    /**
     * Marks this buffer's session as paused (workflow processing should stop).
     * Records the instant at which pause began.
     */
    public void markPaused() {
        this.pausedSince = Instant.now();
        this.paused = true;
    }

    /**
     * Marks this buffer's session as resumed (workflow processing can continue).
     * Clears the pausedSince timestamp.
     */
    public void markResumed() {
        this.paused = false;
        this.pausedSince = null;
    }

    /**
     * Returns true if this buffer's session is currently paused.
     */
    public boolean isPaused() {
        return paused;
    }

    /**
     * Returns the instant at which this buffer's session was paused,
     * or null if not currently paused.
     */
    public Instant getPausedSince() {
        return pausedSince;
    }
}

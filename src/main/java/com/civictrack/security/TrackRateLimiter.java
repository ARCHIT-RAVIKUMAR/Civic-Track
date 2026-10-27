package com.civictrack.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/** Slows down guessing on the public tracking page: at most 20 look-ups per connection per 5 minutes. */
@Component
public class TrackRateLimiter {

    private final int maxRequests;
    private final Duration window;
    private final Clock clock;
    private final ConcurrentHashMap<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

    @Autowired
    public TrackRateLimiter() {
        this(20, Duration.ofMinutes(5), Clock.systemUTC());
    }

    public TrackRateLimiter(int maxRequests, Duration window, Clock clock) {
        this.maxRequests = maxRequests;
        this.window = window;
        this.clock = clock;
    }

    public boolean tryAcquire(String key) {
        if (hits.size() > 10_000) {
            hits.clear(); // protects memory
        }
        Instant now = clock.instant();
        Deque<Instant> recent = hits.computeIfAbsent(key == null ? "unknown" : key, k -> new ArrayDeque<>());
        synchronized (recent) {
            Instant cutoff = now.minus(window);
            while (!recent.isEmpty() && recent.peekFirst().isBefore(cutoff)) {
                recent.pollFirst();
            }
            if (recent.size() >= maxRequests) {
                return false;
            }
            recent.addLast(now);
            return true;
        }
    }
}

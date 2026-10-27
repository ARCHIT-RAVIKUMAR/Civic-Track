package com.civictrack.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrackRateLimiterTest {

    static class TestClock extends Clock {
        Instant now = Instant.parse("2026-01-01T10:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void blocksAfterTheLimitAndAllowsAgainAfterTheWindow() {
        TestClock clock = new TestClock();
        TrackRateLimiter limiter = new TrackRateLimiter(3, Duration.ofMinutes(5), clock);

        assertTrue(limiter.tryAcquire("1.2.3.4"));
        assertTrue(limiter.tryAcquire("1.2.3.4"));
        assertTrue(limiter.tryAcquire("1.2.3.4"));
        assertFalse(limiter.tryAcquire("1.2.3.4"));

        clock.now = clock.now.plus(Duration.ofMinutes(6));
        assertTrue(limiter.tryAcquire("1.2.3.4"));
    }

    @Test
    void eachConnectionHasItsOwnLimit() {
        TrackRateLimiter limiter = new TrackRateLimiter(1, Duration.ofMinutes(5), new TestClock());

        assertTrue(limiter.tryAcquire("1.1.1.1"));
        assertFalse(limiter.tryAcquire("1.1.1.1"));
        assertTrue(limiter.tryAcquire("2.2.2.2"));
    }
}

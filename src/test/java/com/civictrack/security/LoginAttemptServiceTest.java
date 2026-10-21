package com.civictrack.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginAttemptServiceTest {

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

    private TestClock clock;
    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        clock = new TestClock();
        service = new LoginAttemptService(3, Duration.ofMinutes(10), clock);
    }

    @Test
    void accountLocksAfterTooManyFailures() {
        service.recordFailure("a@example.com");
        service.recordFailure("a@example.com");
        assertFalse(service.isLocked("a@example.com"));
        service.recordFailure("a@example.com");
        assertTrue(service.isLocked("a@example.com"));
    }

    @Test
    void lockEndsAfterTheLockDuration() {
        for (int i = 0; i < 3; i++) {
            service.recordFailure("a@example.com");
        }
        assertTrue(service.isLocked("a@example.com"));
        clock.now = clock.now.plus(Duration.ofMinutes(11));
        assertFalse(service.isLocked("a@example.com"));
    }

    @Test
    void successfulLoginResetsTheCounter() {
        service.recordFailure("a@example.com");
        service.recordFailure("a@example.com");
        service.recordSuccess("a@example.com");
        service.recordFailure("a@example.com");
        service.recordFailure("a@example.com");
        assertFalse(service.isLocked("a@example.com"));
    }

    @Test
    void otherUsersAreNotAffectedAndEmailCaseIsIgnored() {
        for (int i = 0; i < 3; i++) {
            service.recordFailure("A@Example.com");
        }
        assertTrue(service.isLocked("a@example.com"));
        assertFalse(service.isLocked("b@example.com"));
    }
}

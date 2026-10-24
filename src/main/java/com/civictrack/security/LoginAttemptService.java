package com.civictrack.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Counts wrong passwords per e-mail. After the limit the account is locked for a while,
 * even if the correct password is then entered. Kept in memory (resets when the app restarts).
 */
@Service
public class LoginAttemptService {

    private record State(int failures, Instant lockedUntil) {
    }

    private final ConcurrentHashMap<String, State> attempts = new ConcurrentHashMap<>();
    private final int maxAttempts;
    private final Duration lockDuration;
    private final Clock clock;

    @Autowired
    public LoginAttemptService(@Value("${civictrack.security.max-login-attempts:5}") int maxAttempts,
                               @Value("${civictrack.security.lock-minutes:10}") long lockMinutes) {
        this(maxAttempts, Duration.ofMinutes(lockMinutes), Clock.systemUTC());
    }

    public LoginAttemptService(int maxAttempts, Duration lockDuration, Clock clock) {
        this.maxAttempts = maxAttempts;
        this.lockDuration = lockDuration;
        this.clock = clock;
    }

    /** @return true when THIS failure caused the account to become locked */
    public boolean recordFailure(String email) {
        if (attempts.size() > 10_000) {
            attempts.clear(); // protects memory from random-email floods
        }
        AtomicBoolean newlyLocked = new AtomicBoolean(false);
        attempts.compute(normalize(email), (key, state) -> {
            Instant now = clock.instant();
            boolean lockActive = state != null && state.lockedUntil() != null && state.lockedUntil().isAfter(now);
            if (lockActive) {
                return state;
            }
            boolean lockExpired = state != null && state.lockedUntil() != null;
            int failures = (state == null || lockExpired) ? 1 : state.failures() + 1;
            Instant lockedUntil = failures >= maxAttempts ? now.plus(lockDuration) : null;
            if (lockedUntil != null) {
                newlyLocked.set(true);
            }
            return new State(failures, lockedUntil);
        });
        return newlyLocked.get();
    }

    public void recordSuccess(String email) {
        attempts.remove(normalize(email));
    }

    public boolean isLocked(String email) {
        State state = attempts.get(normalize(email));
        return state != null && state.lockedUntil() != null && state.lockedUntil().isAfter(clock.instant());
    }

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}

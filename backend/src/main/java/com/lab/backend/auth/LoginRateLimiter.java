package com.lab.backend.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory login throttle for the single-instance monolith (plan §7: rate-limited
 * login). Keyed by IP + email so a flood against one account, or from one host,
 * is blocked without locking every user out. On repeated failures the key is
 * blocked for a cooldown; a successful login clears its counter.
 *
 * <p>Not distributed — if the app is ever scaled horizontally this moves to Redis.
 * A background sweep evicts stale keys so the map can't grow unbounded.
 */
@Component
public class LoginRateLimiter {

    private final int maxAttempts;
    private final Duration window;
    private final Duration blockFor;

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    public LoginRateLimiter(
            @Value("${app.login.max-attempts:5}") int maxAttempts,
            @Value("${app.login.window-seconds:300}") long windowSeconds,
            @Value("${app.login.block-seconds:900}") long blockSeconds) {
        this.maxAttempts = maxAttempts;
        this.window = Duration.ofSeconds(windowSeconds);
        this.blockFor = Duration.ofSeconds(blockSeconds);
    }

    /** Throws {@link TooManyAttemptsException} if the key is currently blocked. */
    public void checkAllowed(String ip, String email) {
        sweepIfDue();
        Attempts a = attempts.get(key(ip, email));
        if (a == null) return;
        Instant now = Instant.now();
        if (a.blockedUntil != null && now.isBefore(a.blockedUntil)) {
            throw new TooManyAttemptsException(Duration.between(now, a.blockedUntil).toSeconds() + 1);
        }
    }

    /** Record a failed attempt; blocks the key once it exceeds {@code maxAttempts} in the window. */
    public void recordFailure(String ip, String email) {
        Instant now = Instant.now();
        attempts.compute(key(ip, email), (k, a) -> {
            if (a == null || now.isAfter(a.windowStart.plus(window))) {
                a = new Attempts(now);
            }
            a.count++;
            if (a.count >= maxAttempts) {
                a.blockedUntil = now.plus(blockFor);
            }
            return a;
        });
    }

    /** Clear the key's counter after a successful login. */
    public void recordSuccess(String ip, String email) {
        attempts.remove(key(ip, email));
    }

    private String key(String ip, String email) {
        return (ip == null ? "?" : ip) + "|" + (email == null ? "?" : email.toLowerCase());
    }

    private volatile Instant lastSweep = Instant.now();

    private void sweepIfDue() {
        Instant now = Instant.now();
        if (now.isBefore(lastSweep.plus(window))) return;
        lastSweep = now;
        attempts.values().removeIf(a ->
                (a.blockedUntil == null || now.isAfter(a.blockedUntil))
                        && now.isAfter(a.windowStart.plus(window)));
    }

    private static final class Attempts {
        Instant windowStart;
        int count;
        Instant blockedUntil;

        Attempts(Instant windowStart) {
            this.windowStart = windowStart;
        }
    }
}

package com.bereta.auth;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Limitation de débit en mémoire (Bucket4j). Suffisant pour une instance unique ;
 * avec plusieurs réplicas (Kubernetes), passer à un stockage partagé (bucket4j-redis).
 */
@Service
public class RateLimiterService {

    public enum Limit {
        LOGIN_PER_IP(20, Duration.ofMinutes(1)),
        LOGIN_PER_IDENTIFIER(10, Duration.ofMinutes(15)),
        REGISTER_PER_IP(5, Duration.ofHours(1)),
        FORGOT_PER_IP(5, Duration.ofMinutes(15)),
        FORGOT_PER_IDENTIFIER(3, Duration.ofMinutes(15)),
        // Analyse de documents (lecture IA des photos : coût par appel)
        IMPORT_ANALYSE_PER_USER(30, Duration.ofHours(1));

        final int capacity;
        final Duration period;

        Limit(int capacity, Duration period) {
            this.capacity = capacity;
            this.period = period;
        }
    }

    private static final int MAX_TRACKED_KEYS = 50_000;

    private final boolean enabled;
    private final Map<String, Bucket> buckets = Collections.synchronizedMap(
            new LinkedHashMap<String, Bucket>(1024, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Bucket> eldest) {
                    return size() > MAX_TRACKED_KEYS;
                }
            });

    public RateLimiterService(@Value("${bereta.rate-limit.enabled:true}") boolean enabled) {
        this.enabled = enabled;
    }

    public void check(Limit limit, String key) {
        if (!enabled || key == null) return;
        Bucket bucket = buckets.computeIfAbsent(limit.name() + ":" + key, ignored -> Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(limit.capacity)
                        .refillGreedy(limit.capacity, limit.period).build())
                .build());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            long seconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()));
            throw new AuthException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                    "Trop de tentatives. Réessayez dans " + seconds + " secondes.", seconds);
        }
    }
}

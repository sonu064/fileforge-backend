package in.bushansirgur.cloudshareapi.security;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal in-memory fixed-window rate limiter (no external dependency).
 * Suitable for a single instance / dev. For multi-instance production, back this with Redis.
 */
@Service
public class RateLimiterService {

    private static final long WINDOW_MS = 60_000L; // 1 minute

    private static class Window {
        volatile long windowStart;
        final AtomicInteger count = new AtomicInteger(0);
    }

    private final ConcurrentHashMap<String, Window> buckets = new ConcurrentHashMap<>();

    /** Returns true if the request is allowed, false if the limit has been exceeded. */
    public boolean allow(String key, int maxRequestsPerMinute) {
        long now = System.currentTimeMillis();
        Window window = buckets.computeIfAbsent(key, k -> {
            Window w = new Window();
            w.windowStart = now;
            return w;
        });
        synchronized (window) {
            if (now - window.windowStart >= WINDOW_MS) {
                window.windowStart = now;
                window.count.set(0);
            }
            return window.count.incrementAndGet() <= maxRequestsPerMinute;
        }
    }
}

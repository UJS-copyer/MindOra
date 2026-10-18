package com.mindora.user.infrastructure.ratelimit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class InMemoryAuthRateLimitAdapterTest {

    @Test
    void limitsWithinWindowAndResetsAfterwards() {
        long[] now = {1_000L};
        InMemoryAuthRateLimitAdapter adapter = new InMemoryAuthRateLimitAdapter(() -> now[0]);
        Duration window = Duration.ofMinutes(1);

        assertTrue(adapter.tryAcquire("k", 2, window));
        assertTrue(adapter.tryAcquire("k", 2, window));
        assertFalse(adapter.tryAcquire("k", 2, window));

        now[0] += window.toMillis();
        assertTrue(adapter.tryAcquire("k", 2, window));
    }

    @Test
    void keepsWindowsPerKey() {
        long[] now = {1_000L};
        InMemoryAuthRateLimitAdapter adapter = new InMemoryAuthRateLimitAdapter(() -> now[0]);
        Duration window = Duration.ofMinutes(1);

        assertTrue(adapter.tryAcquire("a", 1, window));
        assertFalse(adapter.tryAcquire("a", 1, window));
        assertTrue(adapter.tryAcquire("b", 1, window));
    }
}

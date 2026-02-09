package com.example.payment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyCacheTest {

    private IdempotencyCache cache;

    @BeforeEach
    void setUp() {
        cache = new IdempotencyCache();
    }

    @Test
    void contains_shouldReturnFalseForUnknownEvent() {
        assertThat(cache.contains("event-1")).isFalse();
    }

    @Test
    void mark_shouldMakeContainsReturnTrue() {
        cache.mark("event-1");

        assertThat(cache.contains("event-1")).isTrue();
    }

    @Test
    void mark_shouldClearCacheWhenMaxSizeReached() {
        for (int i = 0; i < 10_000; i++) {
            cache.mark("event-" + i);
        }
        assertThat(cache.contains("event-0")).isTrue();

        // This triggers clear (size >= MAX_SIZE) then adds new entry
        cache.mark("event-overflow");

        assertThat(cache.contains("event-0")).isFalse();
        assertThat(cache.contains("event-overflow")).isTrue();
    }
}

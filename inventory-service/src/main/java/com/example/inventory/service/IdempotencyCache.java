package com.example.inventory.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class IdempotencyCache {

    private static final int MAX_SIZE = 10_000;

    private final ConcurrentHashMap<String, Boolean> processedEvents = new ConcurrentHashMap<>();

    public boolean contains(String eventId) {
        return processedEvents.containsKey(eventId);
    }

    public void mark(String eventId) {
        if (processedEvents.size() >= MAX_SIZE) {
            processedEvents.clear();
        }
        processedEvents.put(eventId, Boolean.TRUE);
    }
}

/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.chat;

import com.agentretry.manager.config.ChatProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatRateLimiter {
    private final ChatProperties properties;
    private final Map<String, Deque<Long>> buckets = new ConcurrentHashMap<>();

    public ChatRateLimiter(ChatProperties properties) {
        this.properties = properties;
    }

    public void check(String principal) {
        int limit = Math.max(1, properties.getRequestsPerMinute());
        long now = Instant.now().toEpochMilli();
        long windowStart = now - 60_000L;
        Deque<Long> q = buckets.computeIfAbsent(principal == null ? "anon" : principal, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst() < windowStart) {
                q.pollFirst();
            }
            if (q.size() >= limit) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Chat rate limit exceeded");
            }
            q.addLast(now);
        }
    }
}

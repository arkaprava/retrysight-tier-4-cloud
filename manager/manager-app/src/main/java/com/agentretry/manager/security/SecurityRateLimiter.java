/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.security;

import com.agentretry.manager.config.ManagerSecurityProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SecurityRateLimiter {

    private final ManagerSecurityProperties.RateLimit limits;
    private final Map<String, Deque<Long>> loginFailures = new ConcurrentHashMap<>();
    private final Map<String, Deque<Long>> ingestRequests = new ConcurrentHashMap<>();
    private final Map<String, Deque<Long>> mcpTokenRequests = new ConcurrentHashMap<>();

    public SecurityRateLimiter(ManagerSecurityProperties securityProperties) {
        this.limits = securityProperties.getRateLimit();
    }

    public void checkLoginAllowed(String clientKey) {
        checkWindow(
                loginFailures,
                clientKey == null ? "unknown" : clientKey,
                Math.max(1, limits.getLoginFailuresPerIp()),
                windowMs(limits.getLoginWindowSeconds()),
                "Too many failed login attempts"
        );
    }

    public void recordLoginFailure(String clientKey) {
        record(loginFailures, clientKey == null ? "unknown" : clientKey, windowMs(limits.getLoginWindowSeconds()));
    }

    public void checkIngest(String apiKey) {
        checkWindow(
                ingestRequests,
                apiKey == null ? "unknown" : apiKey,
                Math.max(1, limits.getIngestRequestsPerKey()),
                windowMs(limits.getIngestWindowSeconds()),
                "Ingest rate limit exceeded"
        );
    }

    public void recordIngest(String apiKey) {
        record(ingestRequests, apiKey == null ? "unknown" : apiKey, windowMs(limits.getIngestWindowSeconds()));
    }

    public void checkMcpToken(String clientKey) {
        checkWindow(
                mcpTokenRequests,
                clientKey == null ? "unknown" : clientKey,
                Math.max(1, limits.getMcpTokenRequestsPerIp()),
                windowMs(limits.getLoginWindowSeconds()),
                "MCP token rate limit exceeded"
        );
    }

    public void recordMcpTokenRequest(String clientKey) {
        record(mcpTokenRequests, clientKey == null ? "unknown" : clientKey, windowMs(limits.getLoginWindowSeconds()));
    }

    private void checkWindow(
            Map<String, Deque<Long>> buckets,
            String key,
            int limit,
            long windowMs,
            String message
    ) {
        Deque<Long> q = buckets.computeIfAbsent(key, k -> new ArrayDeque<>());
        long now = Instant.now().toEpochMilli();
        long windowStart = now - windowMs;
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst() < windowStart) {
                q.pollFirst();
            }
            if (q.size() >= limit) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, message);
            }
        }
    }

    private void record(Map<String, Deque<Long>> buckets, String key, long windowMs) {
        Deque<Long> q = buckets.computeIfAbsent(key, k -> new ArrayDeque<>());
        long now = Instant.now().toEpochMilli();
        long windowStart = now - windowMs;
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst() < windowStart) {
                q.pollFirst();
            }
            q.addLast(now);
        }
    }

    private static long windowMs(int seconds) {
        return Math.max(1, seconds) * 1000L;
    }
}

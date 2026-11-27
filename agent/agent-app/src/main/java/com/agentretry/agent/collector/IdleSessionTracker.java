/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.agent.collector;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Tracks per-session last activity and completes sessions that exceed an idle threshold.
 */
public final class IdleSessionTracker {

    private final Map<String, Instant> lastActivity = new ConcurrentHashMap<>();

    public void touch(String sessionKey) {
        if (sessionKey != null && !sessionKey.isBlank()) {
            lastActivity.put(sessionKey, Instant.now());
        }
    }

    public void forget(String sessionKey) {
        if (sessionKey != null) {
            lastActivity.remove(sessionKey);
        }
    }

    public List<String> expireIdle(Duration idleThreshold, Consumer<String> onExpire) {
        if (idleThreshold == null || idleThreshold.isZero() || idleThreshold.isNegative()) {
            return List.of();
        }
        Instant cutoff = Instant.now().minus(idleThreshold);
        List<String> expired = new ArrayList<>();
        for (Map.Entry<String, Instant> entry : lastActivity.entrySet()) {
            if (entry.getValue().isBefore(cutoff)) {
                expired.add(entry.getKey());
            }
        }
        for (String sessionKey : expired) {
            lastActivity.remove(sessionKey);
            if (onExpire != null) {
                onExpire.accept(sessionKey);
            }
        }
        return expired;
    }
}

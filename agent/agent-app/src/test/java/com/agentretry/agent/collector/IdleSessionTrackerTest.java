/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.agent.collector;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdleSessionTrackerTest {

    @Test
    void expiresIdleSessions() throws Exception {
        IdleSessionTracker tracker = new IdleSessionTracker();
        tracker.touch("session-a");

        List<String> expired = new ArrayList<>();
        assertTrue(tracker.expireIdle(Duration.ofMinutes(30), expired::add).isEmpty());

        Thread.sleep(20);
        tracker.expireIdle(Duration.ofMillis(1), expired::add);

        assertEquals(List.of("session-a"), expired);
    }
}

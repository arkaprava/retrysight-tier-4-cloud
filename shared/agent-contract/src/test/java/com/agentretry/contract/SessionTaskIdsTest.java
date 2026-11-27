/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.contract;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SessionTaskIdsTest {

    @Test
    void sameSessionProducesSameTaskIdAcrossCallers() {
        UUID first = SessionTaskIds.forSession(SourceTool.CURSOR, "/path/to/session.jsonl");
        UUID second = SessionTaskIds.forSession(SourceTool.CURSOR, "/path/to/session.jsonl");
        assertEquals(first, second);
    }

    @Test
    void differentToolsOrSessionsProduceDifferentIds() {
        UUID cursor = SessionTaskIds.forSession(SourceTool.CURSOR, "session-a");
        UUID claude = SessionTaskIds.forSession(SourceTool.CLAUDE_CODE, "session-a");
        UUID other = SessionTaskIds.forSession(SourceTool.CURSOR, "session-b");
        assertNotEquals(cursor, claude);
        assertNotEquals(cursor, other);
    }
}

/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.agent.replay;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionLogReplayerTest {

    @TempDir
    Path tempDir;

    @Test
    void readsTrailingSessionLines() throws Exception {
        Path file = tempDir.resolve("session.jsonl");
        Files.writeString(file, """
                {"type":"start"}
                {"type":"edit","file":"A.java"}
                {"type":"complete"}
                """);

        List<SessionLogReplayer.SessionLogLine> lines = SessionLogReplayer.replayFile(file, 2);

        assertEquals(2, lines.size());
        assertTrue(lines.getFirst().content().contains("edit"));
        assertTrue(lines.getLast().content().contains("complete"));
    }
}

/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.agent.replay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the original collector session log from disk for supplemental replay context.
 */
public final class SessionLogReplayer {

    private static final Logger log = LoggerFactory.getLogger(SessionLogReplayer.class);

    private SessionLogReplayer() {
    }

    public static List<SessionLogLine> replayFile(Path sessionFile, int maxLines) {
        if (sessionFile == null || !Files.exists(sessionFile)) {
            return List.of();
        }

        try {
            List<String> lines = Files.readAllLines(sessionFile, StandardCharsets.UTF_8);
            int start = Math.max(0, lines.size() - maxLines);
            List<SessionLogLine> replay = new ArrayList<>();
            for (int i = start; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.isBlank()) {
                    continue;
                }
                replay.add(new SessionLogLine(i + 1, line));
            }
            return replay;
        } catch (IOException ex) {
            log.warn("Failed to replay session file {}: {}", sessionFile, ex.getMessage());
            return List.of();
        }
    }

    public record SessionLogLine(int lineNumber, String content) {
    }
}

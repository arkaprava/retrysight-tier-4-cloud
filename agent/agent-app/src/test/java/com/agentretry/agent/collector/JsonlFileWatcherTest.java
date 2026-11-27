/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.agent.collector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonlFileWatcherTest {

    @TempDir
    Path tempDir;

    @Test
    void readsOnlyNewLines() throws Exception {
        Path file = tempDir.resolve("session.jsonl");
        Files.writeString(file, "{\"line\":1}\n");

        JsonlFileWatcher watcher = new JsonlFileWatcher();
        List<String> lines = new ArrayList<>();

        watcher.scanDirectory(tempDir, ".jsonl", (key, line) -> lines.add(line));
        assertEquals(1, lines.size());

        Files.writeString(file, "{\"line\":1}\n{\"line\":2}\n");
        watcher.scanDirectory(tempDir, ".jsonl", (key, line) -> lines.add(line));
        assertEquals(2, lines.size());
    }

    @Test
    void resetsOffsetWhenFileShrinks() throws Exception {
        Path file = tempDir.resolve("session.jsonl");
        Files.writeString(file, "{\"line\":1}\n{\"line\":2}\n");

        JsonlFileWatcher watcher = new JsonlFileWatcher();
        List<String> lines = new ArrayList<>();
        watcher.scanDirectory(tempDir, ".jsonl", (key, line) -> lines.add(line));
        assertEquals(2, lines.size());

        Files.writeString(file, "{\"rotated\":true}\n");
        lines.clear();
        watcher.scanDirectory(tempDir, ".jsonl", (key, line) -> lines.add(line));
        assertEquals(1, lines.size());
        assertEquals("{\"rotated\":true}", lines.getFirst());
    }
}

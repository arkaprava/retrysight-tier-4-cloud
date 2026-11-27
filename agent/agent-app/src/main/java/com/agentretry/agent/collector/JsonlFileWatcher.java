/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.agent.collector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

/**
 * Incremental JSONL scanner. Streams each file with a BufferedReader and only
 * emits lines past the last seen offset (avoids loading the whole file into a List).
 */
public class JsonlFileWatcher {

    private static final Logger log = LoggerFactory.getLogger(JsonlFileWatcher.class);

    private final java.util.Map<String, Integer> lineOffsets = new ConcurrentHashMap<>();

    public void scanDirectory(Path root, String globSuffix, BiConsumer<String, String> lineHandler) {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(globSuffix))
                    .forEach(path -> readNewLines(path, lineHandler));
        } catch (IOException ex) {
            log.warn("Failed to scan {}", root, ex);
        }
    }

    private void readNewLines(Path file, BiConsumer<String, String> lineHandler) {
        String key = canonicalKey(file);
        int offset = lineOffsets.getOrDefault(key, 0);
        int lineNo = 0;
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (lineNo++ < offset) {
                    continue;
                }
                if (!line.isBlank()) {
                    lineHandler.accept(key, line);
                }
            }
            if (lineNo < offset) {
                log.info("Log rotation detected for {} — resetting offset from {} to 0", file, offset);
                lineOffsets.put(key, 0);
                readNewLines(file, lineHandler);
                return;
            }
            lineOffsets.put(key, lineNo);
        } catch (IOException ex) {
            log.trace("Could not read {}: {}", file, ex.getMessage());
        }
    }

    private static String canonicalKey(Path file) {
        try {
            return file.toRealPath().toString();
        } catch (IOException ex) {
            return file.toString();
        }
    }

    public void reset(String fileKey) {
        lineOffsets.remove(fileKey);
    }
}

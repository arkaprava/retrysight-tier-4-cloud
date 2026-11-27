/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.agent.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LogTimestampParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesIsoTimestampField() throws Exception {
        JsonNodeHolder node = parse("""
                {"type":"user","timestamp":"2026-05-22T14:01:02.110Z","message":{"content":"hello"}}
                """);

        Instant instant = LogTimestampParser.resolve(node.node);

        assertEquals(Instant.parse("2026-05-22T14:01:02.110Z"), instant);
    }

    @Test
    void parsesCodexStartedAtFromPayload() throws Exception {
        JsonNodeHolder node = parse("""
                {
                  "type":"event_msg",
                  "payload":{"type":"task_started","started_at":1781631097}
                }
                """);

        Instant instant = LogTimestampParser.resolve(node.node);

        assertEquals(Instant.ofEpochSecond(1781631097), instant);
    }

    @Test
    void prefersRecordTimestampOverPayloadStartedAt() throws Exception {
        JsonNodeHolder node = parse("""
                {
                  "timestamp":"2026-06-16T17:31:37.960Z",
                  "type":"event_msg",
                  "payload":{"type":"task_started","started_at":1781631097}
                }
                """);

        Instant instant = LogTimestampParser.resolve(node.node);

        assertEquals(Instant.parse("2026-06-16T17:31:37.960Z"), instant);
    }

    @Test
    void parsesCursorEmbeddedTimestamp() throws Exception {
        JsonNodeHolder node = parse("""
                {"role":"user","message":{"content":[{"type":"text","text":"<timestamp>Saturday, Jul 11, 2026, 7:34 PM (UTC+8)</timestamp>\\n<user_query>Fix bug</user_query>"}]}}
                """);

        Instant instant = LogTimestampParser.resolve(node.node);

        assertEquals(Instant.parse("2026-07-11T11:34:00Z"), instant);
    }

    @Test
    void parsesCodexRolloutFilename(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("rollout-2026-06-12T16-08-36-019ed17d.jsonl");
        Files.writeString(file, "");

        Instant instant = LogTimestampParser.resolveFromSessionFile(file.toString());

        assertEquals(Instant.parse("2026-06-12T16:08:36Z"), instant);
    }

    @Test
    void resolvesWarpStartTsSecondsAndMillis() {
        assertEquals(Instant.ofEpochSecond(1_781_631_097L), LogTimestampParser.resolveFromWarpStartTs(1_781_631_097L));
        assertEquals(
                Instant.ofEpochMilli(1_769_006_749_607L),
                LogTimestampParser.resolveFromWarpStartTs(1_769_006_749_607L)
        );
    }

    private JsonNodeHolder parse(String json) throws Exception {
        return new JsonNodeHolder(objectMapper.readTree(json));
    }

    private record JsonNodeHolder(com.fasterxml.jackson.databind.JsonNode node) {
        JsonNodeHolder {
            assertNotNull(node);
        }
    }
}

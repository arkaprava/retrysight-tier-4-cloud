/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.agent.collector;

import com.agentretry.agent.config.AgentProperties;
import com.agentretry.agent.detector.RetryDetector;
import com.agentretry.agent.git.ProjectContextEnricher;
import com.agentretry.agent.uploader.EventBuffer;
import com.agentretry.contract.IngestItemType;
import com.agentretry.contract.TaskStartRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Instant;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CursorCollectorTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesCursorTranscript() throws Exception {
        AgentProperties properties = collectorProperties(tempDir);
        Path file = tempDir.resolve("conv-1.jsonl");
        Files.writeString(file, """
                {"role":"user","message":{"content":[{"type":"text","text":"<user_query>Fix login bug</user_query>"}]}}
                {"role":"assistant","message":{"content":[{"type":"tool_use","name":"StrReplace","input":{"path":"src/Auth.java"}}]}}
                {"type":"turn_ended","status":"success"}
                """);

        EventBuffer buffer = newBuffer(properties);
        CursorCollector collector = newCollector(properties, buffer);

        collector.scan();
        assertTrue(buffer.queueDepth() > 0);
    }

    @Test
    void estimatesTokensWhenUsageMetadataMissing() throws Exception {
        AgentProperties properties = collectorProperties(tempDir);
        Path file = tempDir.resolve("conv-2.jsonl");
        Files.writeString(file, """
                {"role":"user","message":{"content":[{"type":"text","text":"<user_query>Fix login bug with a longer prompt for estimation</user_query>"}]}}
                {"role":"assistant","message":{"content":[{"type":"text","text":"I'll inspect the authentication flow and patch the failing login handler."},{"type":"tool_use","name":"StrReplace","input":{"path":"src/Auth.java"}}]}}
                {"type":"turn_ended","status":"success"}
                """);

        EventBuffer buffer = newBuffer(properties);
        CursorCollector collector = newCollector(properties, buffer);

        collector.scan();

        long inputTokens = 0;
        long outputTokens = 0;
        try (Connection conn = connection(properties);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT item_type, payload FROM pending_events")) {
            while (rs.next()) {
                if (!IngestItemType.TOKEN_USAGE.name().equals(rs.getString("item_type"))) {
                    continue;
                }
                var payload = objectMapper().readTree(rs.getString("payload"));
                inputTokens += payload.path("inputTokens").asLong();
                outputTokens += payload.path("outputTokens").asLong();
            }
        }

        assertTrue(inputTokens > 0, "expected estimated input tokens from user prompt");
        assertTrue(outputTokens > 0, "expected estimated output tokens from assistant reply");
    }

    @Test
    void parsesExplicitUsageMetadata() throws Exception {
        AgentProperties properties = collectorProperties(tempDir);
        Path file = tempDir.resolve("conv-3.jsonl");
        Files.writeString(file, """
                {"role":"user","message":{"content":[{"type":"text","text":"<user_query>Ship it</user_query>"}]}}
                {"role":"assistant","usage":{"input_tokens":1200,"output_tokens":340,"cache_read_input_tokens":50},"model":"claude-sonnet-4"}
                """);

        EventBuffer buffer = newBuffer(properties);
        CursorCollector collector = newCollector(properties, buffer);

        collector.scan();

        boolean foundUsage = false;
        try (Connection conn = connection(properties);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT item_type, payload FROM pending_events")) {
            while (rs.next()) {
                if (!IngestItemType.TOKEN_USAGE.name().equals(rs.getString("item_type"))) {
                    continue;
                }
                var payload = objectMapper().readTree(rs.getString("payload"));
                if (payload.path("outputTokens").asLong() == 340) {
                    assertEquals(1200, payload.path("inputTokens").asLong());
                    assertEquals(50, payload.path("cacheReadTokens").asLong());
                    foundUsage = true;
                }
            }
        }

        assertTrue(foundUsage, "expected explicit usage metadata to be ingested");
    }

    @Test
    void usesEmbeddedTimestampForTaskStart() throws Exception {
        AgentProperties properties = collectorProperties(tempDir);
        Path file = tempDir.resolve("conv-4.jsonl");
        Files.writeString(file, """
                {"role":"user","message":{"content":[{"type":"text","text":"<timestamp>Saturday, Jul 11, 2026, 7:34 PM (UTC+8)</timestamp>\\n<user_query>Fix login bug</user_query>"}]}}
                """);

        EventBuffer buffer = newBuffer(properties);
        CursorCollector collector = newCollector(properties, buffer);

        collector.scan();

        Instant startedAt = null;
        try (Connection conn = connection(properties);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT item_type, payload FROM pending_events")) {
            while (rs.next()) {
                if (!IngestItemType.TASK_START.name().equals(rs.getString("item_type"))) {
                    continue;
                }
                TaskStartRequest request = objectMapper().readValue(rs.getString("payload"), TaskStartRequest.class);
                startedAt = request.startedAt();
            }
        }

        assertEquals(Instant.parse("2026-07-11T11:34:00Z"), startedAt);
    }

    private AgentProperties collectorProperties(Path watchDir) {
        AgentProperties properties = new AgentProperties();
        AgentProperties.CollectorConfig config = new AgentProperties.CollectorConfig();
        config.setType("cursor");
        config.setEnabled(true);
        config.setWatchPath(watchDir.toString());
        config.setDiscoveryMode(AgentProperties.DiscoveryMode.FIXED);
        properties.getPlatform().setAutoDiscovery(false);
        properties.getCollectors().add(config);
        properties.getUpload().setBufferPath(tempDir.resolve("buffer.db").toString());
        return properties;
    }

    private ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    private EventBuffer newBuffer(AgentProperties properties) {
        return new EventBuffer(properties, objectMapper());
    }

    private CursorCollector newCollector(AgentProperties properties, EventBuffer buffer) {
        return new CursorCollector(
                properties,
                buffer,
                new RetryDetector(),
                objectMapper(),
                payloadBuilder(properties),
                projectContextEnricher(properties, buffer),
                new CursorModelResolver(objectMapper(), tempDir.resolve("missing-state.vscdb")),
                new ParseHealthTracker()
        );
    }

    private EventPayloadBuilder payloadBuilder(AgentProperties properties) {
        return new EventPayloadBuilder(properties);
    }

    private ProjectContextEnricher projectContextEnricher(AgentProperties properties, EventBuffer buffer) {
        AgentProperties gitProperties = new AgentProperties();
        gitProperties.getPlatform().getFeatures().setGitContextCapture(false);
        return new ProjectContextEnricher(
                gitProperties,
                new com.agentretry.agent.git.WorkspaceInferenceService(java.util.List.of()),
                new com.agentretry.agent.git.GitProjectResolver(gitProperties),
                buffer
        );
    }

    private Connection connection(AgentProperties properties) throws Exception {
        return DriverManager.getConnection("jdbc:sqlite:" + properties.getUpload().getBufferPath());
    }
}

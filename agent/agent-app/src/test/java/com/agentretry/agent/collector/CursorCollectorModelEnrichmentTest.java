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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CursorCollectorModelEnrichmentTest {

    @TempDir
    Path tempDir;

    @Test
    void attributesModelsFromCursorStateDbWhenTranscriptOmitsModel() throws Exception {
        String composerId = "15d421fe-5694-41a0-867e-e6dae1de2487";
        Path stateDb = tempDir.resolve("state.vscdb");
        seedStateDb(stateDb, composerId);

        Path transcripts = tempDir.resolve("agent-transcripts").resolve(composerId);
        Files.createDirectories(transcripts);
        Path file = transcripts.resolve(composerId + ".jsonl");
        Files.writeString(file, """
                {"role":"user","message":{"content":[{"type":"text","text":"<user_query>Use multiple models</user_query>"}]}}
                {"role":"assistant","message":{"content":[{"type":"text","text":"Working on it"}]}}
                """);

        AgentProperties properties = new AgentProperties();
        AgentProperties.CollectorConfig config = new AgentProperties.CollectorConfig();
        config.setType("cursor");
        config.setEnabled(true);
        config.setWatchPath(tempDir.toString());
        config.setDiscoveryMode(AgentProperties.DiscoveryMode.FIXED);
        properties.getPlatform().setAutoDiscovery(false);
        properties.getCollectors().add(config);
        properties.getUpload().setBufferPath(tempDir.resolve("buffer.db").toString());

        ObjectMapper mapper = objectMapper();
        EventBuffer buffer = new EventBuffer(properties, mapper);
        CursorCollector collector = new CursorCollector(
                properties,
                buffer,
                new RetryDetector(),
                mapper,
                new EventPayloadBuilder(properties),
                new ProjectContextEnricher(
                        properties,
                        new com.agentretry.agent.git.WorkspaceInferenceService(java.util.List.of()),
                        new com.agentretry.agent.git.GitProjectResolver(properties),
                        buffer
                ),
                new CursorModelResolver(mapper, stateDb),
                new ParseHealthTracker()
        );

        collector.scan();

        Set<String> models = new HashSet<>();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + properties.getUpload().getBufferPath());
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT item_type, payload FROM pending_events")) {
            while (rs.next()) {
                if (!IngestItemType.TOKEN_USAGE.name().equals(rs.getString("item_type"))) {
                    continue;
                }
                models.add(mapper.readTree(rs.getString("payload")).path("model").asText());
            }
        }

        assertTrue(models.contains("composer-2.5"), "expected bubble model attribution, got " + models);
        assertTrue(models.contains("grok-4.5"), "expected second bubble model, got " + models);
    }

    private static void seedStateDb(Path db, String composerId) throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + db);
             Statement st = conn.createStatement()) {
            st.execute("""
                    CREATE TABLE cursorDiskKV (
                        key TEXT PRIMARY KEY,
                        value TEXT
                    )
                    """);
            insert(conn, "bubbleId:" + composerId + ":b1", """
                    {"type":1,"createdAt":"2026-07-02T10:49:00Z","modelInfo":{"modelName":"composer-2.5"}}
                    """);
            insert(conn, "bubbleId:" + composerId + ":b2", """
                    {"type":1,"createdAt":"2026-07-02T10:55:00Z","modelInfo":{"modelName":"grok-4.5"}}
                    """);
        }
    }

    private static void insert(Connection conn, String key, String value) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO cursorDiskKV(key, value) VALUES (?, ?)")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }

    private static ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}

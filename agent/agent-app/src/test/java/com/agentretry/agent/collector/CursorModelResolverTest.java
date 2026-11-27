/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.agent.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CursorModelResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void resolvesModelsFromBubbleStateAndComposerConfig() throws Exception {
        Path db = tempDir.resolve("state.vscdb");
        seedStateDb(db, "composer-abc");

        CursorModelResolver resolver = new CursorModelResolver(new ObjectMapper(), db);

        assertEquals(
                List.of("composer-2.5", "grok-4.5", "claude-fable-5"),
                resolver.distinctModels("composer-abc")
        );
        assertEquals(
                "composer-2.5",
                resolver.resolveModel("composer-abc", Instant.parse("2026-07-02T10:50:00Z"), null)
        );
        assertEquals(
                "grok-4.5",
                resolver.resolveModel("composer-abc", Instant.parse("2026-07-02T11:00:00Z"), null)
        );
        assertEquals(
                "claude-sonnet-4",
                resolver.resolveModel("composer-abc", Instant.parse("2026-07-02T11:00:00Z"), "claude-sonnet-4")
        );
        assertTrue(CursorModelResolver.isConcreteModel("grok-4.5"));
        assertTrue(!CursorModelResolver.isConcreteModel("cursor"));
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
            insert(conn, "bubbleId:" + composerId + ":b3", """
                    {"type":2,"createdAt":"2026-07-02T10:56:00Z","text":"user"}
                    """);
            insert(conn, "composerData:" + composerId, """
                    {"modelConfig":{"modelName":"claude-fable-5","selectedModels":[{"modelId":"claude-fable-5"}]}}
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
}

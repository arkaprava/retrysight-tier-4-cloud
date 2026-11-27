/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.agent.discovery;

import com.agentretry.agent.config.AgentProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollectorDiscoveryServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void marksRecentLogsAsFound() throws Exception {
        Path cursorDir = tempDir.resolve("cursor/projects");
        Files.createDirectories(cursorDir);
        Path log = cursorDir.resolve("session.jsonl");
        Files.writeString(log, "{\"role\":\"user\"}\n");

        AgentProperties properties = propertiesWithAutoDiscovery();
        AgentProperties.CollectorConfig config = new AgentProperties.CollectorConfig();
        config.setType("cursor");
        config.setEnabled(true);
        config.setWatchPath(cursorDir.toString());
        config.setDiscoveryMode(AgentProperties.DiscoveryMode.AUTO);
        properties.getCollectors().add(config);

        CollectorDiscoveryService service = new CollectorDiscoveryService(properties, List.of());
        CollectorDiscoveryService.Result result = service.refresh().stream()
                .filter(r -> "cursor".equals(r.configType()))
                .findFirst()
                .orElseThrow();

        assertEquals(CollectorDiscoveryService.Status.FOUND, result.status());
        assertTrue(service.isDiscoveredEnabled("cursor"));
    }

    @Test
    void marksOldLogsAsStale() throws Exception {
        Path cursorDir = tempDir.resolve("cursor/projects");
        Files.createDirectories(cursorDir);
        Path log = cursorDir.resolve("session.jsonl");
        Files.writeString(log, "{\"role\":\"user\"}\n");
        Instant stale = Instant.now().minusSeconds(60L * 60 * 24 * 40);
        Files.setLastModifiedTime(log, java.nio.file.attribute.FileTime.from(stale));

        AgentProperties properties = propertiesWithAutoDiscovery();
        AgentProperties.CollectorConfig config = new AgentProperties.CollectorConfig();
        config.setType("cursor");
        config.setEnabled(true);
        config.setWatchPath(cursorDir.toString());
        properties.getCollectors().add(config);

        CollectorDiscoveryService service = new CollectorDiscoveryService(properties, List.of());
        CollectorDiscoveryService.Result result = service.refresh().stream()
                .filter(r -> "cursor".equals(r.configType()))
                .findFirst()
                .orElseThrow();

        assertEquals(CollectorDiscoveryService.Status.STALE, result.status());
    }

    private static AgentProperties propertiesWithAutoDiscovery() {
        AgentProperties properties = new AgentProperties();
        properties.getPlatform().setAutoDiscovery(true);
        properties.getDiscovery().setRecencyDays(30);
        return properties;
    }
}

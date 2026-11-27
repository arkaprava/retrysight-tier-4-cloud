/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.agent.collector;

import com.agentretry.agent.config.AgentProperties;
import com.agentretry.agent.detector.RetryDetector;
import com.agentretry.agent.git.ProjectContextEnricher;
import com.agentretry.agent.uploader.EventBuffer;
import com.agentretry.contract.SourceTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StandardJsonlCollectorTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesRoleBasedJsonlAndEnqueuesEvents() throws Exception {
        AgentProperties properties = new AgentProperties();
        AgentProperties.CollectorConfig config = new AgentProperties.CollectorConfig();
        config.setType("continue");
        config.setEnabled(true);
        config.setWatchPath(tempDir.toString());
        properties.getCollectors().add(config);
        properties.getUpload().setBufferPath(tempDir.resolve("buffer.db").toString());

        Path file = tempDir.resolve("session-1.jsonl");
        Files.writeString(file, """
                {"role":"user","message":{"content":[{"type":"text","text":"Refactor auth module"}]}}
                {"role":"assistant","message":{"content":[{"type":"tool_use","name":"edit","input":{"path":"src/Auth.java"}}]}}
                """);

        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        EventBuffer buffer = new EventBuffer(properties, mapper);

        AgentProperties gitProperties = new AgentProperties();
        gitProperties.getPlatform().getFeatures().setGitContextCapture(false);
        ProjectContextEnricher enricher = new ProjectContextEnricher(
                gitProperties,
                new com.agentretry.agent.git.WorkspaceInferenceService(List.of()),
                new com.agentretry.agent.git.GitProjectResolver(gitProperties),
                buffer
        );

        StandardJsonlCollector collector = new StandardJsonlCollector(
                properties,
                buffer,
                new RetryDetector(),
                mapper,
                new EventPayloadBuilder(properties),
                enricher,
                SourceTool.CONTINUE,
                "continue",
                tempDir.toString()
        );

        collector.scan();
        assertTrue(buffer.queueDepth() > 0);
    }
}

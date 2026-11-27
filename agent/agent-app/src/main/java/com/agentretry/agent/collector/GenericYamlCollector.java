/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.agent.collector;

import com.agentretry.agent.config.AgentProperties;
import com.agentretry.agent.config.PlatformPaths;
import com.agentretry.agent.detector.RetryDetector;
import com.agentretry.agent.git.ProjectContextEnricher;
import com.agentretry.agent.uploader.EventBuffer;
import com.agentretry.contract.SourceTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase 3 generic collector driven by YAML sidecar definitions referenced from agent config.
 */
@Component
public class GenericYamlCollector extends AbstractSessionCollector {

    private final ObjectMapper objectMapper;
    private final EventBuffer eventBuffer;
    private final RetryDetector retryDetector;
    private final EventPayloadBuilder payloadBuilder;
    private final ProjectContextEnricher projectContextEnricher;
    private final List<YamlCollectorDefinition> definitions = new ArrayList<>();
    private final List<StandardJsonlCollector> delegates = new ArrayList<>();

    public GenericYamlCollector(
            AgentProperties properties,
            ObjectMapper objectMapper,
            EventBuffer eventBuffer,
            RetryDetector retryDetector,
            EventPayloadBuilder payloadBuilder,
            ProjectContextEnricher projectContextEnricher
    ) {
        super(properties);
        this.objectMapper = objectMapper;
        this.eventBuffer = eventBuffer;
        this.retryDetector = retryDetector;
        this.payloadBuilder = payloadBuilder;
        this.projectContextEnricher = projectContextEnricher;
        reloadDefinitions();
    }

    @Override
    public SourceTool sourceTool() {
        return delegates.isEmpty() ? SourceTool.CURSOR : delegates.getFirst().sourceTool();
    }

    @Override
    public boolean isEnabled() {
        return properties.getPlatform().isPhase3YamlRegistry() && !definitions.isEmpty();
    }

    @Override
    public List<Path> watchPaths() {
        return definitions.stream().map(YamlCollectorDefinition::watchPath).toList();
    }

    @Override
    public void scan() {
        for (StandardJsonlCollector delegate : delegates) {
            delegate.scan();
        }
    }

    private void reloadDefinitions() {
        definitions.clear();
        delegates.clear();
        if (!properties.getPlatform().isPhase3YamlRegistry()) {
            return;
        }
        for (AgentProperties.CollectorConfig config : properties.getCollectors()) {
            if (!"yaml".equalsIgnoreCase(config.getType()) || !config.isEnabled()) {
                continue;
            }
            Path yamlPath = PlatformPaths.expandUserPath(config.getWatchPath());
            if (!Files.isRegularFile(yamlPath)) {
                continue;
            }
            try {
                JsonNode root = objectMapper.readTree(yamlPath.toFile());
                String watchPath = root.path("watchPath").asText();
                if (watchPath.isBlank()) {
                    continue;
                }
                SourceTool tool;
                try {
                    tool = SourceTool.valueOf(root.path("sourceTool").asText("CURSOR").toUpperCase());
                } catch (Exception ex) {
                    tool = SourceTool.CURSOR;
                }
                Path expanded = PlatformPaths.expandUserPath(watchPath);
                definitions.add(new YamlCollectorDefinition(expanded, tool));
                delegates.add(new StandardJsonlCollector(
                        properties,
                        eventBuffer,
                        retryDetector,
                        objectMapper,
                        payloadBuilder,
                        projectContextEnricher,
                        tool,
                        "yaml",
                        expanded.toString()
                ) {
                    @Override
                    public boolean isEnabled() {
                        return true;
                    }

                    @Override
                    public List<Path> watchPaths() {
                        return List.of(expanded);
                    }
                });
            } catch (Exception ignored) {
            }
        }
    }

    private record YamlCollectorDefinition(Path watchPath, SourceTool sourceTool) {
    }
}

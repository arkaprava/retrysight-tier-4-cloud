/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.agent.collector;

import com.agentretry.agent.config.AgentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Phase 3: load sidecar YAML collector definitions and optionally auto-discover watch paths.
 */
@Component
public class CollectorAutoDiscovery {

    private static final Logger log = LoggerFactory.getLogger(CollectorAutoDiscovery.class);

    private final AgentProperties properties;
    private final ObjectMapper objectMapper;

    public CollectorAutoDiscovery(AgentProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void discover() {
        if (!properties.getPlatform().isPhase3YamlRegistry()) {
            return;
        }

        loadYamlSidecars();
        // Runtime log discovery with recency checks is handled by CollectorDiscoveryService.
    }

    private void loadYamlSidecars() {
        for (AgentProperties.CollectorConfig config : properties.getCollectors()) {
            if (!"yaml".equalsIgnoreCase(config.getType()) || !config.isEnabled()) {
                continue;
            }
            Path yamlPath = Path.of(expandHome(config.getWatchPath()));
            if (!Files.isRegularFile(yamlPath)) {
                log.warn("YAML collector config not found: {}", yamlPath);
                continue;
            }
            try {
                JsonNode root = objectMapper.readTree(yamlPath.toFile());
                String configType = root.path("configType").asText();
                String watchPath = root.path("watchPath").asText();
                if (configType.isBlank() || watchPath.isBlank()) {
                    log.warn("Invalid YAML collector config (missing configType/watchPath): {}", yamlPath);
                    continue;
                }
                upsertCollector(configType, watchPath);
                log.info("Loaded YAML collector definition {} from {}", configType, yamlPath);
            } catch (Exception ex) {
                log.warn("Failed to load YAML collector config {}: {}", yamlPath, ex.getMessage());
            }
        }
    }

    private void autoDiscoverFromCatalog() {
        for (com.agentretry.contract.SourceToolDescriptor descriptor : com.agentretry.contract.SourceToolCatalog.all()) {
            if (!properties.isCollectorAvailable(descriptor.configType())) {
                continue;
            }
            if (hasCollectorConfig(descriptor.configType())) {
                continue;
            }
            Path watchPath = Path.of(expandHome(descriptor.defaultWatchPath()));
            if (Files.exists(watchPath)) {
                upsertCollector(descriptor.configType(), descriptor.defaultWatchPath());
                log.info("Auto-discovered collector {} at {}", descriptor.configType(), watchPath);
            }
        }
    }

    private boolean hasCollectorConfig(String configType) {
        return properties.getCollectors().stream()
                .anyMatch(c -> configType.equalsIgnoreCase(c.getType()));
    }

    private void upsertCollector(String configType, String watchPath) {
        for (AgentProperties.CollectorConfig existing : properties.getCollectors()) {
            if (configType.equalsIgnoreCase(existing.getType())) {
                if (existing.getWatchPath() == null || existing.getWatchPath().isBlank()) {
                    existing.setWatchPath(watchPath);
                }
                existing.setEnabled(true);
                return;
            }
        }
        AgentProperties.CollectorConfig config = new AgentProperties.CollectorConfig();
        config.setType(configType);
        config.setEnabled(true);
        config.setWatchPath(watchPath);
        properties.getCollectors().add(config);
    }

    private String expandHome(String path) {
        if (path == null) {
            return "";
        }
        if (path.startsWith("~/")) {
            return System.getProperty("user.home") + path.substring(1);
        }
        return path;
    }
}

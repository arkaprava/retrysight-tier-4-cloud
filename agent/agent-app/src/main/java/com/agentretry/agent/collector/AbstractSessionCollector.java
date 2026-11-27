/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.agent.collector;

import com.agentretry.agent.config.AgentProperties;
import com.agentretry.agent.config.PlatformPaths;
import com.agentretry.agent.discovery.CollectorDiscoveryService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public abstract class AbstractSessionCollector implements SessionCollector {

    protected final AgentProperties properties;
    protected final JsonlFileWatcher fileWatcher = new JsonlFileWatcher();

    @Autowired
    protected CollectorDiscoveryService discoveryService;

    protected AbstractSessionCollector(AgentProperties properties) {
        this.properties = properties;
    }

    protected boolean isCollectorEnabled(String type) {
        if (!properties.isCollectorAvailable(type)) {
            return false;
        }
        Optional<AgentProperties.CollectorConfig> explicit = findCollectorConfig(type);
        if (explicit.isPresent() && !explicit.get().isEnabled()) {
            return false;
        }
        if (explicit.isPresent() && explicit.get().getDiscoveryMode() == AgentProperties.DiscoveryMode.FIXED) {
            return true;
        }
        if (properties.getPlatform().isAutoDiscovery() && discoveryService != null) {
            return discoveryService.isDiscoveredEnabled(type);
        }
        return explicit.map(AgentProperties.CollectorConfig::isEnabled).orElse(false);
    }

    protected List<java.nio.file.Path> resolveWatchPaths(String type, String defaultPath) {
        Set<java.nio.file.Path> paths = new LinkedHashSet<>();
        Optional<AgentProperties.CollectorConfig> explicit = findCollectorConfig(type);
        explicit.flatMap(cfg -> optionalPath(cfg.getWatchPath())).ifPresent(paths::add);

        if (properties.getPlatform().isAutoDiscovery() && discoveryService != null) {
            discoveryService.discoveredWatchPath(type).ifPresent(paths::add);
        }

        if (paths.isEmpty()) {
            paths.add(PlatformPaths.expandUserPath(defaultPath));
        }
        return new ArrayList<>(paths);
    }

    protected Optional<AgentProperties.CollectorConfig> findCollectorConfig(String type) {
        return properties.getCollectors().stream()
                .filter(c -> type.equalsIgnoreCase(c.getType()))
                .findFirst();
    }

    private static Optional<java.nio.file.Path> optionalPath(String watchPath) {
        if (watchPath == null || watchPath.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(PlatformPaths.expandUserPath(watchPath));
    }

    protected String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    protected String extractUserQuery(String text) {
        if (text == null) {
            return "";
        }
        int start = text.indexOf("<user_query>");
        int end = text.indexOf("</user_query>");
        if (start >= 0 && end > start) {
            return text.substring(start + 12, end).trim();
        }
        return text.trim();
    }
}

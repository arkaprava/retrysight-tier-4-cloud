/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.agent.discovery;

import com.agentretry.agent.config.AgentProperties;
import com.agentretry.agent.config.PlatformPaths;
import com.agentretry.agent.collector.ConfigTypedCollector;
import com.agentretry.agent.collector.SessionCollector;
import com.agentretry.contract.SourceTool;
import com.agentretry.contract.SourceToolCatalog;
import com.agentretry.contract.SourceToolDescriptor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CollectorDiscoveryService {

    public enum Status {
        FOUND,
        STALE,
        MISSING,
        SKIPPED,
        CONFIGURED
    }

    public record Result(
            String configType,
            Path watchPath,
            Status status,
            String detail,
            Instant newestLogAt
    ) {
    }

    private static final int PROBE_MAX_DEPTH = 3;

    private final AgentProperties properties;
    private final List<SessionCollector> sessionCollectors;
    private final Map<String, Path> activeWatchPaths = new ConcurrentHashMap<>();
    private volatile List<Result> lastResults = List.of();

    public CollectorDiscoveryService(AgentProperties properties, @Lazy List<SessionCollector> sessionCollectors) {
        this.properties = properties;
        this.sessionCollectors = sessionCollectors;
    }

    public List<Result> refresh() {
        Duration recency = Duration.ofDays(Math.max(1, properties.getDiscovery().getRecencyDays()));
        Instant recencyCutoff = Instant.now().minus(recency);

        List<Result> results = new ArrayList<>();
        activeWatchPaths.clear();

        for (SourceToolDescriptor descriptor : SourceToolCatalog.forTier(properties.getPlatform().getInstallationTier())) {
            if (!properties.isCollectorAvailable(descriptor.configType())) {
                continue;
            }
            if (descriptor.logFormat() == SourceToolDescriptor.LogFormat.OTEL) {
                results.add(new Result(descriptor.configType(), null, Status.SKIPPED, "otel ingest", null));
                continue;
            }
            if (!isImplementedCollector(descriptor.configType())) {
                results.add(new Result(descriptor.configType(), null, Status.SKIPPED, "collector not implemented", null));
                continue;
            }

            Optional<AgentProperties.CollectorConfig> explicit = explicitConfig(descriptor.configType());
            if (explicit.isPresent() && explicit.get().getDiscoveryMode() == AgentProperties.DiscoveryMode.FIXED) {
                Path configured = PlatformPaths.expandUserPath(explicit.get().getWatchPath());
                Status status = classify(configured, descriptor.logFormat(), recencyCutoff);
                String detail = explicitDetail(configured, descriptor.logFormat());
                Instant newest = PlatformPaths.newestLogTimestamp(configured, descriptor.logFormat(), PROBE_MAX_DEPTH);
                results.add(new Result(descriptor.configType(), configured, Status.CONFIGURED, detail, newest));
                if (explicit.get().isEnabled() && status != Status.MISSING) {
                    activeWatchPaths.put(normalizeType(descriptor.configType()), configured);
                }
                continue;
            }

            List<Path> candidates = new ArrayList<>();
            explicit.flatMap(cfg -> optionalPath(cfg.getWatchPath())).ifPresent(candidates::add);
            candidates.addAll(PlatformPaths.candidateWatchPaths(descriptor));

            Optional<Path> selected = PlatformPaths.selectBestCandidate(candidates, descriptor.logFormat(), PROBE_MAX_DEPTH);
            if (selected.isEmpty()) {
                Path fallback = candidates.isEmpty() ? null : candidates.getFirst();
                results.add(new Result(descriptor.configType(), fallback, Status.MISSING, "no readable logs", null));
                continue;
            }

            Path watchPath = selected.get();
            Status status = classify(watchPath, descriptor.logFormat(), recencyCutoff);
            Instant newest = PlatformPaths.newestLogTimestamp(watchPath, descriptor.logFormat(), PROBE_MAX_DEPTH);
            String detail = explicitDetail(watchPath, descriptor.logFormat());
            results.add(new Result(descriptor.configType(), watchPath, status, detail, newest));

            boolean enabled = explicit.map(AgentProperties.CollectorConfig::isEnabled).orElse(true);
            if (enabled && status == Status.FOUND) {
                activeWatchPaths.put(normalizeType(descriptor.configType()), watchPath);
            }
        }

        lastResults = List.copyOf(results);
        return lastResults;
    }

    public List<Result> lastResults() {
        return lastResults;
    }

    public boolean isDiscoveredEnabled(String configType) {
        if (!properties.getPlatform().isAutoDiscovery()) {
            return false;
        }
        return activeWatchPaths.containsKey(normalizeType(configType));
    }

    public Optional<Path> discoveredWatchPath(String configType) {
        return Optional.ofNullable(activeWatchPaths.get(normalizeType(configType)));
    }

    private Status classify(Path path, SourceToolDescriptor.LogFormat format, Instant recencyCutoff) {
        if (path == null || !Files.exists(path)) {
            return Status.MISSING;
        }
        if (!PlatformPaths.hasReadableLogSignal(path, format, PROBE_MAX_DEPTH)) {
            return Status.MISSING;
        }
        Instant newest = PlatformPaths.newestLogTimestamp(path, format, PROBE_MAX_DEPTH);
        if (newest == null || newest.isBefore(recencyCutoff)) {
            return Status.STALE;
        }
        return Status.FOUND;
    }

    private static String explicitDetail(Path path, SourceToolDescriptor.LogFormat format) {
        if (path == null) {
            return "path unavailable";
        }
        Instant newest = PlatformPaths.newestLogTimestamp(path, format, PROBE_MAX_DEPTH);
        if (newest == null) {
            return "exists, no matching logs";
        }
        return "newest log " + newest;
    }

    private Optional<AgentProperties.CollectorConfig> explicitConfig(String configType) {
        return properties.getCollectors().stream()
                .filter(cfg -> configType.equalsIgnoreCase(cfg.getType()))
                .findFirst();
    }

    private static Optional<Path> optionalPath(String watchPath) {
        if (watchPath == null || watchPath.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(PlatformPaths.expandUserPath(watchPath));
    }

    private static String normalizeType(String configType) {
        return configType == null ? "" : configType.toLowerCase(Locale.ROOT);
    }

    private boolean isImplementedCollector(String configType) {
        if (sessionCollectors.stream().anyMatch(c -> matchesConfigType(c, configType))) {
            return true;
        }
        return properties.getCollectors().stream()
                .anyMatch(cfg -> configType.equalsIgnoreCase(cfg.getType()) && cfg.isEnabled());
    }

    private static boolean matchesConfigType(SessionCollector collector, String configType) {
        if (collector instanceof ConfigTypedCollector typed) {
            return configType.equalsIgnoreCase(typed.configType());
        }
        return SourceToolDescriptor.findByConfigType(configType)
                .map(descriptor -> collector.sourceTool() == descriptor.id())
                .orElse(false);
    }
}

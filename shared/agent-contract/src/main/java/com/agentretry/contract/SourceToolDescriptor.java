/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.contract;

import java.util.List;
import java.util.Optional;

/**
 * Canonical metadata for supported AI IDE / agent surfaces.
 */
public record SourceToolDescriptor(
        SourceTool id,
        String label,
        String configType,
        InstallationTier minimumTier,
        String defaultWatchPath,
        LogFormat logFormat,
        List<String> capabilities
) {
    public enum LogFormat {
        JSONL,
        EVENTS_JSONL,
        SQLITE,
        JSON_DOC,
        YAML_CONFIG,
        /** OpenTelemetry push (e.g. Claude Cowork OTLP logs). */
        OTEL
    }

    public boolean availableAt(InstallationTier tier) {
        return tier.includes(minimumTier);
    }

    public static Optional<SourceToolDescriptor> find(SourceTool tool) {
        return SourceToolCatalog.all().stream()
                .filter(d -> d.id() == tool)
                .findFirst();
    }

    public static Optional<SourceToolDescriptor> findByConfigType(String configType) {
        if (configType == null) {
            return Optional.empty();
        }
        return SourceToolCatalog.all().stream()
                .filter(d -> d.configType().equalsIgnoreCase(configType))
                .findFirst();
    }
}

/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.graphql;

import com.agentretry.contract.InstallationTier;
import com.agentretry.contract.SourceTool;

import java.util.List;

public record SourceToolInfo(
        SourceTool id,
        String label,
        String configType,
        InstallationTier minimumTier,
        String defaultWatchPath,
        String logFormat,
        List<String> capabilities,
        boolean enabled
) {
}

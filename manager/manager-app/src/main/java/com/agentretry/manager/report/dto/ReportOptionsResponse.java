/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.report.dto;

import java.util.List;

public record ReportOptionsResponse(
        List<Integer> enabledPhases,
        int maxDateRangeDays,
        List<PhaseOption> phases,
        List<DatasetOption> datasets,
        List<FormatOption> formats
) {
    public record PhaseOption(
            int phase,
            String label,
            String description,
            boolean enabled
    ) {
    }

    public record DatasetOption(
            String id,
            String label,
            String description,
            int phase,
            boolean available,
            Integer requiresPhase,
            List<String> formats,
            List<String> requiredFilters,
            List<String> optionalFilters
    ) {
    }

    public record FormatOption(
            String id,
            String label,
            String description,
            int phase,
            boolean available,
            Integer requiresPhase
    ) {
    }
}

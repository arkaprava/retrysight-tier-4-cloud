/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.report;

import java.util.Set;

public record DatasetDefinition(
        ReportDataset id,
        String label,
        String description,
        int phase,
        Set<ReportFormat> supportedFormats,
        Set<String> requiredFilters,
        Set<String> optionalFilters,
        boolean implemented
) {
}

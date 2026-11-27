/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.report.dto;

import com.agentretry.manager.report.ReportDataset;
import com.agentretry.manager.report.ReportFormat;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record ReportGenerateRequest(
        @NotNull ReportDataset dataset,
        @NotNull ReportFormat format,
        Instant from,
        Instant to,
        ReportFilters filters,
        Integer phase
) {
}

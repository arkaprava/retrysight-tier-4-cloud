/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.report.dto;

import java.util.UUID;

public record ReportFilters(
        String sourceTool,
        UUID agentId,
        UUID taskId
) {
}

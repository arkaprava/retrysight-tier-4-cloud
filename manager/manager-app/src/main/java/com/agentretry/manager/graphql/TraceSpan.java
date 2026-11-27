/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.graphql;

import java.time.Instant;
import java.util.UUID;

public record TraceSpan(
        UUID id,
        UUID taskId,
        String externalTraceId,
        String name,
        String kind,
        Instant startAt,
        long durationMs,
        String status,
        UUID parentSpanId,
        String inputRedacted,
        String outputRedacted,
        String sourceTool
) {
}

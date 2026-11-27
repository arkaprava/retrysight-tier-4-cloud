/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.graphql;

import java.util.UUID;

public record SpanNode(
        UUID id,
        String name,
        String kind,
        long startMs,
        long durationMs,
        String status,
        UUID parentId,
        String attributes
) {
}

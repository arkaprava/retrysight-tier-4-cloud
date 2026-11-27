/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.graphql;

import java.util.UUID;

public record HotDataPartition(
        UUID id,
        String dataset,
        String periodStart,
        String periodEnd,
        String status,
        String notes,
        String createdAt
) {
}

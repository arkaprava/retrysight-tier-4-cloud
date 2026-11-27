/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.graphql;

import java.time.Instant;
import java.util.UUID;

public record ApiKeySummary(
        UUID id,
        String name,
        String keyPrefix,
        String role,
        boolean active,
        Instant createdAt,
        Instant lastUsedAt,
        Instant revokedAt
) {
}

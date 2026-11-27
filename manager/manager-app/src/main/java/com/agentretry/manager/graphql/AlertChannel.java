/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.graphql;

import java.time.Instant;
import java.util.UUID;

public record AlertChannel(
        UUID id,
        String name,
        String channelType,
        String target,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}

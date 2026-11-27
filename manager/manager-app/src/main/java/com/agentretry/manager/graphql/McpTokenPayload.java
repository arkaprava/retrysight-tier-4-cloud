/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.graphql;

import java.time.Instant;

public record McpTokenPayload(
        String token,
        String email,
        Instant expiresAt
) {
}

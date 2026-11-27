/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.contract;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CollectorDiscoveryEntry(
        String configType,
        CollectorDiscoveryStatus status,
        String watchPath,
        String detail,
        Instant newestLogAt
) {
}

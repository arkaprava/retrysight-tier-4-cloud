/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.contract;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskEventRequest(
        @NotNull UUID taskId,
        @NotNull TaskEventType eventType,
        @NotNull Instant occurredAt,
        boolean retry,
        int sequenceNum,
        Map<String, Object> payload
) {
}

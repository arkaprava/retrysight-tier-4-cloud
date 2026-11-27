/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.contract;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskStartRequest(
        @NotNull UUID taskId,
        @NotBlank String externalSessionId,
        @NotNull SourceTool sourceTool,
        @NotBlank String description,
        Instant startedAt,
        Map<String, Object> metadata
) {
}

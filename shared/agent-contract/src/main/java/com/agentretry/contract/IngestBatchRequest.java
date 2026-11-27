/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.contract;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record IngestBatchRequest(
        AgentHeartbeatRequest heartbeat,
        @Valid List<TaskStartRequest> taskStarts,
        @Valid List<@NotNull @Valid IngestBatchItem> items
) {
}

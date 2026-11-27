/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.contract;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record IngestBatchResponse(
        int accepted,
        int skipped,
        List<String> errors,
        UUID agentId
) {
    public IngestBatchResponse(int accepted, int skipped, List<String> errors) {
        this(accepted, skipped, errors, null);
    }
}

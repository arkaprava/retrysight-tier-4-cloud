/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.graphql;

import com.agentretry.manager.eval.EvalRunLabel;
import com.agentretry.manager.eval.EvalRunStatus;
import com.agentretry.manager.eval.EvalTrigger;

import java.time.Instant;
import java.util.UUID;

public record EvalRun(
        UUID id,
        UUID definitionId,
        EvalDefinition definition,
        UUID taskId,
        UUID eventId,
        Double score,
        EvalRunLabel label,
        String explanation,
        EvalRunStatus status,
        EvalTrigger triggeredBy,
        Instant createdAt
) {
}

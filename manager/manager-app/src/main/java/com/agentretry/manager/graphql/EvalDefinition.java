/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.graphql;

import com.agentretry.manager.eval.EvalDefinitionKind;
import com.agentretry.manager.eval.EvalTargetScope;

import java.time.Instant;
import java.util.UUID;

public record EvalDefinition(
        UUID id,
        String slug,
        String name,
        EvalDefinitionKind kind,
        String promptTemplate,
        String ruleConfig,
        EvalTargetScope targetScope,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}

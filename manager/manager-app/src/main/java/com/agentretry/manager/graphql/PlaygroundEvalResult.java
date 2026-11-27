/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.graphql;

import java.util.UUID;

public record PlaygroundEvalResult(
        UUID taskId,
        UUID definitionId,
        String label,
        Double score,
        String explanation,
        String promptUsed
) {
}

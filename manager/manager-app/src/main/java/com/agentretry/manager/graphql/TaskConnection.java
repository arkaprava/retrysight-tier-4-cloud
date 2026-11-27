/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.graphql;

import com.agentretry.manager.domain.TaskEntity;

import java.util.List;

public record TaskConnection(
        List<TaskEntity> items,
        int totalCount
) {
}

/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.graphql;

import com.agentretry.contract.TaskEventType;

public record ModelRetryTypeMetric(String model, TaskEventType eventType, int count) {
}

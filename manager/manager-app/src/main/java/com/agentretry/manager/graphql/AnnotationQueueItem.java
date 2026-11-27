/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.graphql;

import com.agentretry.manager.eval.AnnotationQueueReason;
import com.agentretry.manager.eval.AnnotationQueueStatus;

import java.time.Instant;
import java.util.UUID;

public record AnnotationQueueItem(
        UUID id,
        UUID taskId,
        AnnotationQueueReason reason,
        String assignedTo,
        AnnotationQueueStatus status,
        String notes,
        String createdBy,
        Instant createdAt,
        Instant completedAt
) {
}

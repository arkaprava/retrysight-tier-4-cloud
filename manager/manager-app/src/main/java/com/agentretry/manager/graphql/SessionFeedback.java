/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.graphql;

import com.agentretry.manager.eval.FeedbackType;

import java.time.Instant;
import java.util.UUID;

public record SessionFeedback(
        UUID id,
        UUID taskId,
        UUID eventId,
        int rating,
        String comment,
        FeedbackType feedbackType,
        String createdBy,
        Instant createdAt
) {
}

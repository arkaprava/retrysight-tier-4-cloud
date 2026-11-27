/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.clickhouse;

import com.agentretry.manager.domain.TaskEntity;
import com.agentretry.manager.domain.TaskEventEntity;
import com.agentretry.manager.domain.TokenUsageEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Publishes analytics rows after the RDBMS transaction commits (dual-write).
 */
@Component
public class AnalyticsSinkPublisher {

    private final AnalyticsEventSink sink;

    public AnalyticsSinkPublisher(AnalyticsEventSink sink) {
        this.sink = sink;
    }

    public boolean isEnabled() {
        return sink.isEnabled();
    }

    public void publishTokenUsageAfterCommit(TokenUsageEntity usage) {
        if (!sink.isEnabled() || usage == null) {
            return;
        }
        // Materialize row inside the open persistence context (lazy agent/task).
        TokenUsageAnalyticsRow row = toTokenRow(usage);
        runAfterCommit(() -> sink.publishTokenUsage(row));
    }

    public void publishTaskEventAfterCommit(TaskEventEntity event) {
        if (!sink.isEnabled() || event == null) {
            return;
        }
        TaskEventAnalyticsRow row = toEventRow(event);
        runAfterCommit(() -> sink.publishTaskEvent(row));
    }

    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private static TokenUsageAnalyticsRow toTokenRow(TokenUsageEntity usage) {
        TaskEntity task = usage.getTask();
        return new TokenUsageAnalyticsRow(
                usage.getId(),
                task != null ? task.getId() : null,
                task != null && task.getAgent() != null ? task.getAgent().getId() : null,
                task != null && task.getAgent() != null ? task.getAgent().getDeveloperEmail() : null,
                task != null && task.getSourceTool() != null ? task.getSourceTool().name() : null,
                usage.getTurnIndex(),
                usage.getModel(),
                usage.getInputTokens(),
                usage.getOutputTokens(),
                usage.getCacheReadTokens(),
                usage.getCacheWriteTokens(),
                usage.getReasoningTokens(),
                usage.getEstimatedCostUsd(),
                usage.getRecordedAt(),
                usage.getIdempotencyKey()
        );
    }

    private static TaskEventAnalyticsRow toEventRow(TaskEventEntity event) {
        TaskEntity task = event.getTask();
        return new TaskEventAnalyticsRow(
                event.getId(),
                task != null ? task.getId() : null,
                task != null && task.getAgent() != null ? task.getAgent().getId() : null,
                task != null && task.getAgent() != null ? task.getAgent().getDeveloperEmail() : null,
                task != null && task.getSourceTool() != null ? task.getSourceTool().name() : null,
                event.getEventType() != null ? event.getEventType().name() : null,
                event.isRetry(),
                event.getSequenceNum(),
                event.getOccurredAt(),
                event.getIdempotencyKey()
        );
    }
}

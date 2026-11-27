/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "token_usage")
public class TokenUsageEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private TaskEntity task;

    @Column(name = "turn_index", nullable = false)
    private int turnIndex;

    private String model;

    @Column(name = "input_tokens", nullable = false)
    private long inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private long outputTokens;

    @Column(name = "cache_read_tokens", nullable = false)
    private long cacheReadTokens;

    @Column(name = "cache_write_tokens", nullable = false)
    private long cacheWriteTokens;

    @Column(name = "reasoning_tokens", nullable = false)
    private long reasoningTokens;

    @Column(name = "estimated_cost_usd")
    private BigDecimal estimatedCostUsd;

    @Enumerated(EnumType.STRING)
    @Column(name = "token_source", nullable = false)
    private TokenSource tokenSource = TokenSource.ESTIMATED;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public TaskEntity getTask() {
        return task;
    }

    public void setTask(TaskEntity task) {
        this.task = task;
    }

    public int getTurnIndex() {
        return turnIndex;
    }

    public void setTurnIndex(int turnIndex) {
        this.turnIndex = turnIndex;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public long getInputTokens() {
        return inputTokens;
    }

    public void setInputTokens(long inputTokens) {
        this.inputTokens = inputTokens;
    }

    public long getOutputTokens() {
        return outputTokens;
    }

    public void setOutputTokens(long outputTokens) {
        this.outputTokens = outputTokens;
    }

    public long getCacheReadTokens() {
        return cacheReadTokens;
    }

    public void setCacheReadTokens(long cacheReadTokens) {
        this.cacheReadTokens = cacheReadTokens;
    }

    public long getCacheWriteTokens() {
        return cacheWriteTokens;
    }

    public void setCacheWriteTokens(long cacheWriteTokens) {
        this.cacheWriteTokens = cacheWriteTokens;
    }

    public long getReasoningTokens() {
        return reasoningTokens;
    }

    public void setReasoningTokens(long reasoningTokens) {
        this.reasoningTokens = reasoningTokens;
    }

    public BigDecimal getEstimatedCostUsd() {
        return estimatedCostUsd;
    }

    public void setEstimatedCostUsd(BigDecimal estimatedCostUsd) {
        this.estimatedCostUsd = estimatedCostUsd;
    }

    public TokenSource getTokenSource() {
        return tokenSource;
    }

    public void setTokenSource(TokenSource tokenSource) {
        this.tokenSource = tokenSource;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(Instant recordedAt) {
        this.recordedAt = recordedAt;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
}

/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cost_alerts")
public class CostAlertEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "budget_id", nullable = false)
    private CostBudgetEntity budget;

    @Column(name = "triggered_at", nullable = false)
    private Instant triggeredAt;

    @Column(name = "spend_usd", nullable = false)
    private BigDecimal spendUsd;

    @Column(name = "limit_usd", nullable = false)
    private BigDecimal limitUsd;

    @Column(nullable = false)
    private String message;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public CostBudgetEntity getBudget() {
        return budget;
    }

    public void setBudget(CostBudgetEntity budget) {
        this.budget = budget;
    }

    public Instant getTriggeredAt() {
        return triggeredAt;
    }

    public void setTriggeredAt(Instant triggeredAt) {
        this.triggeredAt = triggeredAt;
    }

    public BigDecimal getSpendUsd() {
        return spendUsd;
    }

    public void setSpendUsd(BigDecimal spendUsd) {
        this.spendUsd = spendUsd;
    }

    public BigDecimal getLimitUsd() {
        return limitUsd;
    }

    public void setLimitUsd(BigDecimal limitUsd) {
        this.limitUsd = limitUsd;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }
}

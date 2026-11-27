/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "billing_actuals")
public class BillingActualEntity {

    @Id
    private UUID id;

    @Column(name = "period_start", nullable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;

    @Column(name = "actual_cost_usd", nullable = false)
    private BigDecimal actualCostUsd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BillingActualSource source;

    private String notes;

    @Column(name = "imported_at", nullable = false)
    private Instant importedAt;

    @Column(name = "imported_by", nullable = false)
    private String importedBy;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Instant getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(Instant periodStart) {
        this.periodStart = periodStart;
    }

    public Instant getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(Instant periodEnd) {
        this.periodEnd = periodEnd;
    }

    public BigDecimal getActualCostUsd() {
        return actualCostUsd;
    }

    public void setActualCostUsd(BigDecimal actualCostUsd) {
        this.actualCostUsd = actualCostUsd;
    }

    public BillingActualSource getSource() {
        return source;
    }

    public void setSource(BillingActualSource source) {
        this.source = source;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getImportedAt() {
        return importedAt;
    }

    public void setImportedAt(Instant importedAt) {
        this.importedAt = importedAt;
    }

    public String getImportedBy() {
        return importedBy;
    }

    public void setImportedBy(String importedBy) {
        this.importedBy = importedBy;
    }
}

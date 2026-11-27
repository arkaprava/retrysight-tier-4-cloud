/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "model_pricing")
public class ModelPricingEntity {

    @Id
    @Column(name = "model_key", nullable = false)
    private String modelKey;

    @Column(name = "input_per_million", nullable = false)
    private BigDecimal inputPerMillion;

    @Column(name = "output_per_million", nullable = false)
    private BigDecimal outputPerMillion;

    @Column(name = "cache_read_per_million", nullable = false)
    private BigDecimal cacheReadPerMillion;

    @Column(name = "cache_write_per_million", nullable = false)
    private BigDecimal cacheWritePerMillion;

    @Column(name = "reasoning_per_million", nullable = false)
    private BigDecimal reasoningPerMillion;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public String getModelKey() {
        return modelKey;
    }

    public void setModelKey(String modelKey) {
        this.modelKey = modelKey;
    }

    public BigDecimal getInputPerMillion() {
        return inputPerMillion;
    }

    public void setInputPerMillion(BigDecimal inputPerMillion) {
        this.inputPerMillion = inputPerMillion;
    }

    public BigDecimal getOutputPerMillion() {
        return outputPerMillion;
    }

    public void setOutputPerMillion(BigDecimal outputPerMillion) {
        this.outputPerMillion = outputPerMillion;
    }

    public BigDecimal getCacheReadPerMillion() {
        return cacheReadPerMillion;
    }

    public void setCacheReadPerMillion(BigDecimal cacheReadPerMillion) {
        this.cacheReadPerMillion = cacheReadPerMillion;
    }

    public BigDecimal getCacheWritePerMillion() {
        return cacheWritePerMillion;
    }

    public void setCacheWritePerMillion(BigDecimal cacheWritePerMillion) {
        this.cacheWritePerMillion = cacheWritePerMillion;
    }

    public BigDecimal getReasoningPerMillion() {
        return reasoningPerMillion;
    }

    public void setReasoningPerMillion(BigDecimal reasoningPerMillion) {
        this.reasoningPerMillion = reasoningPerMillion;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

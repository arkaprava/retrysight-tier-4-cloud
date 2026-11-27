/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.domain;

import com.agentretry.manager.eval.EvalExperimentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "eval_experiments")
public class EvalExperimentEntity {
    @Id
    private UUID id;
    @Column(nullable = false, length = 128)
    private String name;
    @Column(name = "dataset_id", nullable = false)
    private UUID datasetId;
    @Column(name = "baseline_definition_id", nullable = false)
    private UUID baselineDefinitionId;
    @Column(name = "variant_definition_id", nullable = false)
    private UUID variantDefinitionId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EvalExperimentStatus status;
    private String notes;
    @Column(name = "created_by")
    private String createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "completed_at")
    private Instant completedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public UUID getDatasetId() { return datasetId; }
    public void setDatasetId(UUID datasetId) { this.datasetId = datasetId; }
    public UUID getBaselineDefinitionId() { return baselineDefinitionId; }
    public void setBaselineDefinitionId(UUID baselineDefinitionId) { this.baselineDefinitionId = baselineDefinitionId; }
    public UUID getVariantDefinitionId() { return variantDefinitionId; }
    public void setVariantDefinitionId(UUID variantDefinitionId) { this.variantDefinitionId = variantDefinitionId; }
    public EvalExperimentStatus getStatus() { return status; }
    public void setStatus(EvalExperimentStatus status) { this.status = status; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}

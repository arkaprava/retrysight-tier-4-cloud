/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "eval_experiment_results")
public class EvalExperimentResultEntity {
    @Id
    private UUID id;
    @Column(name = "experiment_id", nullable = false)
    private UUID experimentId;
    @Column(name = "task_id", nullable = false)
    private UUID taskId;
    @Column(nullable = false, length = 16)
    private String variant;
    @Column(name = "eval_run_id")
    private UUID evalRunId;
    private Double score;
    @Column(length = 16)
    private String label;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getExperimentId() { return experimentId; }
    public void setExperimentId(UUID experimentId) { this.experimentId = experimentId; }
    public UUID getTaskId() { return taskId; }
    public void setTaskId(UUID taskId) { this.taskId = taskId; }
    public String getVariant() { return variant; }
    public void setVariant(String variant) { this.variant = variant; }
    public UUID getEvalRunId() { return evalRunId; }
    public void setEvalRunId(UUID evalRunId) { this.evalRunId = evalRunId; }
    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

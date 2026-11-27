/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.domain;

import com.agentretry.manager.eval.EvalRunLabel;
import com.agentretry.manager.eval.EvalRunStatus;
import com.agentretry.manager.eval.EvalTrigger;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "eval_runs")
public class EvalRunEntity {
    @Id
    private UUID id;
    @Column(name = "definition_id", nullable = false)
    private UUID definitionId;
    @Column(name = "task_id", nullable = false)
    private UUID taskId;
    @Column(name = "event_id")
    private UUID eventId;
    private Double score;
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private EvalRunLabel label;
    private String explanation;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EvalRunStatus status;
    @Enumerated(EnumType.STRING)
    @Column(name = "triggered_by", nullable = false, length = 32)
    private EvalTrigger triggeredBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getDefinitionId() { return definitionId; }
    public void setDefinitionId(UUID definitionId) { this.definitionId = definitionId; }
    public UUID getTaskId() { return taskId; }
    public void setTaskId(UUID taskId) { this.taskId = taskId; }
    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }
    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }
    public EvalRunLabel getLabel() { return label; }
    public void setLabel(EvalRunLabel label) { this.label = label; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
    public EvalRunStatus getStatus() { return status; }
    public void setStatus(EvalRunStatus status) { this.status = status; }
    public EvalTrigger getTriggeredBy() { return triggeredBy; }
    public void setTriggeredBy(EvalTrigger triggeredBy) { this.triggeredBy = triggeredBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

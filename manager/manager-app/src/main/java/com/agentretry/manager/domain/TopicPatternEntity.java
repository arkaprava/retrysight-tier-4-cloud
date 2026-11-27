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
@Table(name = "topic_patterns")
public class TopicPatternEntity {
    @Id
    private UUID id;
    @Column(nullable = false)
    private String label;
    @Column(name = "task_count", nullable = false)
    private int taskCount;
    @Column(name = "fail_rate", nullable = false)
    private double failRate;
    @Column(name = "sample_task_ids")
    private String sampleTaskIds;
    @Column(name = "period_start", nullable = false)
    private Instant periodStart;
    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;
    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;
    @Column(name = "relevance_score", nullable = false)
    private double relevanceScore = 0.5;
    @Column(nullable = false)
    private boolean suspicious;
    @Column(name = "flag_reason", length = 512)
    private String flagReason;
    @Column(name = "window_days", nullable = false)
    private int windowDays = 30;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public int getTaskCount() { return taskCount; }
    public void setTaskCount(int taskCount) { this.taskCount = taskCount; }
    public double getFailRate() { return failRate; }
    public void setFailRate(double failRate) { this.failRate = failRate; }
    public String getSampleTaskIds() { return sampleTaskIds; }
    public void setSampleTaskIds(String sampleTaskIds) { this.sampleTaskIds = sampleTaskIds; }
    public Instant getPeriodStart() { return periodStart; }
    public void setPeriodStart(Instant periodStart) { this.periodStart = periodStart; }
    public Instant getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(Instant periodEnd) { this.periodEnd = periodEnd; }
    public Instant getComputedAt() { return computedAt; }
    public void setComputedAt(Instant computedAt) { this.computedAt = computedAt; }
    public double getRelevanceScore() { return relevanceScore; }
    public void setRelevanceScore(double relevanceScore) { this.relevanceScore = relevanceScore; }
    public boolean isSuspicious() { return suspicious; }
    public void setSuspicious(boolean suspicious) { this.suspicious = suspicious; }
    public String getFlagReason() { return flagReason; }
    public void setFlagReason(String flagReason) { this.flagReason = flagReason; }
    public int getWindowDays() { return windowDays; }
    public void setWindowDays(int windowDays) { this.windowDays = windowDays; }
}

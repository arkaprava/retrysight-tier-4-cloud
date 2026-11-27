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
@Table(name = "retention_settings")
public class RetentionSettingsEntity {
    @Id
    private UUID id;
    @Column(name = "task_events_days", nullable = false)
    private int taskEventsDays;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public int getTaskEventsDays() { return taskEventsDays; }
    public void setTaskEventsDays(int taskEventsDays) { this.taskEventsDays = taskEventsDays; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

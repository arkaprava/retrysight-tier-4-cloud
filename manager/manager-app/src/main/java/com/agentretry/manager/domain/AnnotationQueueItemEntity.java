/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.domain;

import com.agentretry.manager.eval.AnnotationQueueReason;
import com.agentretry.manager.eval.AnnotationQueueStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "annotation_queue_items")
public class AnnotationQueueItemEntity {
    @Id
    private UUID id;
    @Column(name = "task_id", nullable = false)
    private UUID taskId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private AnnotationQueueReason reason;
    @Column(name = "assigned_to")
    private String assignedTo;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AnnotationQueueStatus status;
    private String notes;
    @Column(name = "created_by")
    private String createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "completed_at")
    private Instant completedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTaskId() { return taskId; }
    public void setTaskId(UUID taskId) { this.taskId = taskId; }
    public AnnotationQueueReason getReason() { return reason; }
    public void setReason(AnnotationQueueReason reason) { this.reason = reason; }
    public String getAssignedTo() { return assignedTo; }
    public void setAssignedTo(String assignedTo) { this.assignedTo = assignedTo; }
    public AnnotationQueueStatus getStatus() { return status; }
    public void setStatus(AnnotationQueueStatus status) { this.status = status; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}

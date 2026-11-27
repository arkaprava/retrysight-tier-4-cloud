/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "eval_dataset_items")
@IdClass(EvalDatasetItemEntity.Key.class)
public class EvalDatasetItemEntity {
    @Id
    @Column(name = "dataset_id")
    private UUID datasetId;
    @Id
    @Column(name = "task_id")
    private UUID taskId;
    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    public UUID getDatasetId() { return datasetId; }
    public void setDatasetId(UUID datasetId) { this.datasetId = datasetId; }
    public UUID getTaskId() { return taskId; }
    public void setTaskId(UUID taskId) { this.taskId = taskId; }
    public Instant getAddedAt() { return addedAt; }
    public void setAddedAt(Instant addedAt) { this.addedAt = addedAt; }

    public static class Key implements Serializable {
        private UUID datasetId;
        private UUID taskId;

        public Key() {
        }

        public Key(UUID datasetId, UUID taskId) {
            this.datasetId = datasetId;
            this.taskId = taskId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key key)) return false;
            return Objects.equals(datasetId, key.datasetId) && Objects.equals(taskId, key.taskId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(datasetId, taskId);
        }
    }
}

/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "task_id_aliases")
public class TaskIdAliasEntity {

    @Id
    @Column(name = "alias_id", nullable = false)
    private UUID aliasId;

    @Column(name = "canonical_task_id", nullable = false)
    private UUID canonicalTaskId;

    public UUID getAliasId() {
        return aliasId;
    }

    public void setAliasId(UUID aliasId) {
        this.aliasId = aliasId;
    }

    public UUID getCanonicalTaskId() {
        return canonicalTaskId;
    }

    public void setCanonicalTaskId(UUID canonicalTaskId) {
        this.canonicalTaskId = canonicalTaskId;
    }
}

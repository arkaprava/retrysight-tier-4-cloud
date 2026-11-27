/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.domain;

import com.agentretry.manager.eval.EvalDefinitionKind;
import com.agentretry.manager.eval.EvalTargetScope;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "eval_definitions")
public class EvalDefinitionEntity {
    @Id
    private UUID id;
    @Column(nullable = false, unique = true, length = 128)
    private String slug;
    @Column(nullable = false, length = 128)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EvalDefinitionKind kind;
    @Column(name = "prompt_template")
    private String promptTemplate;
    @Column(name = "rule_config")
    private String ruleConfig;
    @Enumerated(EnumType.STRING)
    @Column(name = "target_scope", nullable = false, length = 32)
    private EvalTargetScope targetScope = EvalTargetScope.TASK;
    @Column(nullable = false)
    private boolean enabled = true;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public EvalDefinitionKind getKind() { return kind; }
    public void setKind(EvalDefinitionKind kind) { this.kind = kind; }
    public String getPromptTemplate() { return promptTemplate; }
    public void setPromptTemplate(String promptTemplate) { this.promptTemplate = promptTemplate; }
    public String getRuleConfig() { return ruleConfig; }
    public void setRuleConfig(String ruleConfig) { this.ruleConfig = ruleConfig; }
    public EvalTargetScope getTargetScope() { return targetScope; }
    public void setTargetScope(EvalTargetScope targetScope) { this.targetScope = targetScope; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

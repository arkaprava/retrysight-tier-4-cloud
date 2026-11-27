/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.eval;

import com.agentretry.manager.domain.EvalDefinitionEntity;
import com.agentretry.manager.eval.EvalDefinitionKind;
import com.agentretry.manager.eval.EvalTargetScope;
import com.agentretry.manager.graphql.EvalDefinition;
import com.agentretry.manager.repository.EvalDefinitionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class EvalDefinitionService {
    private final EvalDefinitionRepository repository;
    private final ObjectMapper objectMapper;

    public EvalDefinitionService(EvalDefinitionRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void seedDefaults() {
        if (repository.count() > 0) {
            return;
        }
        seedManaged("managed.high-retry", "High retry count",
                "{\"evaluator\":\"HIGH_RETRY\",\"maxRetries\":3}");
        seedManaged("managed.tool-failure-rate", "Tool failure rate",
                "{\"evaluator\":\"TOOL_FAILURE_RATE\",\"maxFailureRate\":0.25}");
        seedManaged("managed.cost-outlier", "Cost outlier",
                "{\"evaluator\":\"COST_OUTLIER\",\"multiplier\":3.0,\"minCostUsd\":0.01}");
        seedLlmJudge();
    }

    private void seedManaged(String slug, String name, String ruleConfig) {
        EvalDefinitionEntity entity = new EvalDefinitionEntity();
        entity.setId(UUID.randomUUID());
        entity.setSlug(slug);
        entity.setName(name);
        entity.setKind(EvalDefinitionKind.MANAGED);
        entity.setRuleConfig(ruleConfig);
        entity.setTargetScope(EvalTargetScope.TASK);
        entity.setEnabled(true);
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        repository.save(entity);
    }

    private void seedLlmJudge() {
        EvalDefinitionEntity entity = new EvalDefinitionEntity();
        entity.setId(UUID.randomUUID());
        entity.setSlug("llm.session-quality");
        entity.setName("Session quality (LLM judge)");
        entity.setKind(EvalDefinitionKind.LLM_JUDGE);
        entity.setTargetScope(EvalTargetScope.TASK);
        entity.setEnabled(false);
        entity.setPromptTemplate("""
                Evaluate this coding-agent session for overall quality and efficiency.

                Task: {{task_description}}
                Tool: {{source_tool}}
                Retries: {{retry_count}}

                Session replay:
                {{session_replay}}

                Consider: excessive retries, test failures, rejected edits, and whether the session \
                appears to have made productive progress.
                """);
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        repository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<EvalDefinition> list() {
        return repository.findAllByOrderByNameAsc().stream().map(this::toGraphql).toList();
    }

    @Transactional(readOnly = true)
    public List<EvalDefinitionEntity> listEnabledEntities() {
        return repository.findByEnabledTrueOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public EvalDefinitionEntity requireEntity(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Eval definition not found"));
    }

    @Transactional
    public EvalDefinition create(
            String slug,
            String name,
            EvalDefinitionKind kind,
            String promptTemplate,
            String ruleConfig,
            EvalTargetScope targetScope,
            boolean enabled
    ) {
        if (slug == null || slug.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "slug is required");
        }
        if (repository.existsBySlug(slug.trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "slug already exists");
        }
        EvalDefinitionEntity entity = new EvalDefinitionEntity();
        entity.setId(UUID.randomUUID());
        entity.setSlug(slug.trim());
        entity.setName(name != null ? name.trim() : slug.trim());
        entity.setKind(kind != null ? kind : EvalDefinitionKind.RULE);
        entity.setPromptTemplate(promptTemplate);
        entity.setRuleConfig(ruleConfig);
        entity.setTargetScope(targetScope != null ? targetScope : EvalTargetScope.TASK);
        entity.setEnabled(enabled);
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return toGraphql(repository.save(entity));
    }

    @Transactional
    public EvalDefinition update(
            UUID id,
            String name,
            String promptTemplate,
            String ruleConfig,
            Boolean enabled
    ) {
        EvalDefinitionEntity entity = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Eval definition not found"));
        if (name != null && !name.isBlank()) {
            entity.setName(name.trim());
        }
        if (promptTemplate != null) {
            entity.setPromptTemplate(promptTemplate);
        }
        if (ruleConfig != null) {
            entity.setRuleConfig(ruleConfig);
        }
        if (enabled != null) {
            entity.setEnabled(enabled);
        }
        entity.setUpdatedAt(Instant.now());
        return toGraphql(repository.save(entity));
    }

    @Transactional
    public boolean delete(UUID id) {
        if (!repository.existsById(id)) {
            return false;
        }
        repository.deleteById(id);
        return true;
    }

    private EvalDefinition toGraphql(EvalDefinitionEntity entity) {
        return new EvalDefinition(
                entity.getId(),
                entity.getSlug(),
                entity.getName(),
                entity.getKind(),
                entity.getPromptTemplate(),
                entity.getRuleConfig(),
                entity.getTargetScope(),
                entity.isEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}

/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.eval;

import com.agentretry.manager.config.EvalProperties;
import com.agentretry.manager.domain.EvalDefinitionEntity;
import com.agentretry.manager.domain.EvalRunEntity;
import com.agentretry.manager.domain.TaskEntity;
import com.agentretry.manager.domain.TaskEventEntity;
import com.agentretry.manager.domain.TokenUsageEntity;
import com.agentretry.manager.platform.FeatureGateService;
import com.agentretry.manager.repository.EvalRunRepository;
import com.agentretry.manager.repository.TaskEventRepository;
import com.agentretry.manager.repository.TaskRepository;
import com.agentretry.manager.repository.TokenUsageRepository;
import com.agentretry.manager.service.CostAnalyticsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class EvalRunnerService {
    private static final Logger log = LoggerFactory.getLogger(EvalRunnerService.class);

    private final FeatureGateService featureGateService;
    private final EvalProperties evalProperties;
    private final EvalDefinitionService definitionService;
    private final EvalRunRepository runRepository;
    private final TaskRepository taskRepository;
    private final TaskEventRepository taskEventRepository;
    private final TokenUsageRepository tokenUsageRepository;
    private final CostAnalyticsService costAnalyticsService;
    private final LlmJudgeEvaluator llmJudgeEvaluator;
    private final AnnotationQueueService annotationQueueService;
    private final ObjectMapper objectMapper;

    public EvalRunnerService(
            FeatureGateService featureGateService,
            EvalProperties evalProperties,
            EvalDefinitionService definitionService,
            EvalRunRepository runRepository,
            TaskRepository taskRepository,
            TaskEventRepository taskEventRepository,
            TokenUsageRepository tokenUsageRepository,
            CostAnalyticsService costAnalyticsService,
            LlmJudgeEvaluator llmJudgeEvaluator,
            AnnotationQueueService annotationQueueService,
            ObjectMapper objectMapper
    ) {
        this.featureGateService = featureGateService;
        this.evalProperties = evalProperties;
        this.definitionService = definitionService;
        this.runRepository = runRepository;
        this.taskRepository = taskRepository;
        this.taskEventRepository = taskEventRepository;
        this.tokenUsageRepository = tokenUsageRepository;
        this.costAnalyticsService = costAnalyticsService;
        this.llmJudgeEvaluator = llmJudgeEvaluator;
        this.annotationQueueService = annotationQueueService;
        this.objectMapper = objectMapper;
    }

    public boolean isEnabled() {
        return featureGateService.isEvaluationsEnabled() && evalProperties.isEnabled();
    }

    @Transactional
    public List<EvalRunEntity> runOnlineEvaluations(UUID taskId) {
        return runEvaluations(taskId, EvalTrigger.ONLINE, true);
    }

    @Transactional
    public List<EvalRunEntity> runManualEvaluations(UUID taskId) {
        return runEvaluations(taskId, EvalTrigger.MANUAL, false);
    }

    @Transactional
    public EvalRunEntity runSingleDefinition(
            UUID taskId,
            UUID definitionId,
            EvalTrigger trigger,
            String promptOverride
    ) {
        if (!isEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Evaluations are not enabled");
        }
        TaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
        EvalDefinitionEntity definition = definitionService.requireEntity(definitionId);

        List<TaskEventEntity> events = taskEventRepository.findByTaskIdOrderBySequenceNumAsc(taskId);
        List<TokenUsageEntity> tokenTurns = tokenUsageRepository.findByTaskIdOrderByTurnIndexAsc(taskId);
        double medianCost = costAnalyticsService.medianTaskCostUsd(task.getSourceTool());

        try {
            return executeDefinition(definition, task, events, tokenTurns, medianCost, trigger, promptOverride);
        } catch (Exception ex) {
            log.warn("Eval {} failed for task {}: {}", definition.getSlug(), taskId, ex.getMessage());
            return saveRun(definition.getId(), taskId, null, EvalRunStatus.FAILED, trigger,
                    null, null, "Evaluator error: " + ex.getMessage());
        }
    }

    private List<EvalRunEntity> runEvaluations(UUID taskId, EvalTrigger trigger, boolean skipIfExists) {
        if (!isEnabled()) {
            return List.of();
        }
        TaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));

        List<TaskEventEntity> events = taskEventRepository.findByTaskIdOrderBySequenceNumAsc(taskId);
        List<TokenUsageEntity> tokenTurns = tokenUsageRepository.findByTaskIdOrderByTurnIndexAsc(taskId);
        double medianCost = costAnalyticsService.medianTaskCostUsd(task.getSourceTool());

        List<EvalRunEntity> results = new ArrayList<>();
        for (EvalDefinitionEntity definition : definitionService.listEnabledEntities()) {
            if (skipIfExists && trigger == EvalTrigger.ONLINE
                    && runRepository.existsByTaskIdAndDefinitionIdAndTriggeredBy(taskId, definition.getId(), EvalTrigger.ONLINE)) {
                continue;
            }
            try {
                EvalRunEntity run = executeDefinition(definition, task, events, tokenTurns, medianCost, trigger, null);
                results.add(run);
                if (run.getLabel() == EvalRunLabel.FAIL && evalProperties.isAutoEnqueueOnFail()) {
                    annotationQueueService.enqueueIfAbsent(taskId, AnnotationQueueReason.EVAL_FAILED, "system");
                }
            } catch (Exception ex) {
                log.warn("Eval {} failed for task {}: {}", definition.getSlug(), taskId, ex.getMessage());
                results.add(saveRun(definition.getId(), taskId, null, EvalRunStatus.FAILED, trigger,
                        null, null, "Evaluator error: " + ex.getMessage()));
            }
        }
        return results;
    }

    private EvalRunEntity executeDefinition(
            EvalDefinitionEntity definition,
            TaskEntity task,
            List<TaskEventEntity> events,
            List<TokenUsageEntity> tokenTurns,
            double medianCost,
            EvalTrigger trigger,
            String promptOverride
    ) {
        ManagedEvaluatorEngine.EvalOutcome outcome = switch (definition.getKind()) {
            case MANAGED, RULE -> ManagedEvaluatorEngine.evaluate(
                    definition.getRuleConfig(),
                    task,
                    events,
                    tokenTurns,
                    medianCost,
                    objectMapper
            );
            case LLM_JUDGE -> llmJudgeEvaluator.evaluate(definition, task, promptOverride);
        };

        if (outcome.skipped()) {
            return saveRun(definition.getId(), task.getId(), null, EvalRunStatus.SKIPPED, trigger,
                    null, null, outcome.explanation());
        }
        return saveRun(definition.getId(), task.getId(), null, EvalRunStatus.COMPLETE, trigger,
                outcome.score(), outcome.label(), outcome.explanation());
    }

    private EvalRunEntity saveRun(
            UUID definitionId,
            UUID taskId,
            UUID eventId,
            EvalRunStatus status,
            EvalTrigger trigger,
            Double score,
            EvalRunLabel label,
            String explanation
    ) {
        EvalRunEntity entity = new EvalRunEntity();
        entity.setId(UUID.randomUUID());
        entity.setDefinitionId(definitionId);
        entity.setTaskId(taskId);
        entity.setEventId(eventId);
        entity.setStatus(status);
        entity.setTriggeredBy(trigger);
        entity.setScore(score);
        entity.setLabel(label);
        entity.setExplanation(explanation);
        entity.setCreatedAt(Instant.now());
        return runRepository.save(entity);
    }
}

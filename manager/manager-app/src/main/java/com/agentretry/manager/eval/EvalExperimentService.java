/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.eval;

import com.agentretry.manager.domain.EvalDefinitionEntity;
import com.agentretry.manager.domain.EvalExperimentEntity;
import com.agentretry.manager.domain.EvalExperimentResultEntity;
import com.agentretry.manager.domain.EvalRunEntity;
import com.agentretry.manager.eval.EvalRunLabel;
import com.agentretry.manager.graphql.EvalExperiment;
import com.agentretry.manager.graphql.EvalExperimentComparison;
import com.agentretry.manager.graphql.EvalExperimentResult;
import com.agentretry.manager.repository.EvalDefinitionRepository;
import com.agentretry.manager.repository.EvalExperimentRepository;
import com.agentretry.manager.repository.EvalExperimentResultRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class EvalExperimentService {
    private final EvalExperimentRepository experimentRepository;
    private final EvalExperimentResultRepository resultRepository;
    private final EvalDefinitionRepository definitionRepository;
    private final EvalDatasetService datasetService;
    private final EvalRunnerService runnerService;

    public EvalExperimentService(
            EvalExperimentRepository experimentRepository,
            EvalExperimentResultRepository resultRepository,
            EvalDefinitionRepository definitionRepository,
            EvalDatasetService datasetService,
            EvalRunnerService runnerService
    ) {
        this.experimentRepository = experimentRepository;
        this.resultRepository = resultRepository;
        this.definitionRepository = definitionRepository;
        this.datasetService = datasetService;
        this.runnerService = runnerService;
    }

    @Transactional(readOnly = true)
    public List<EvalExperiment> list() {
        return experimentRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toGraphql)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EvalExperimentResult> results(UUID experimentId) {
        return resultRepository.findByExperimentIdOrderByCreatedAtAsc(experimentId).stream()
                .map(this::toResultGraphql)
                .toList();
    }

    @Transactional
    public EvalExperiment create(
            String name,
            UUID datasetId,
            UUID baselineDefinitionId,
            UUID variantDefinitionId,
            String createdBy
    ) {
        requireDefinition(baselineDefinitionId);
        requireDefinition(variantDefinitionId);
        datasetService.get(datasetId);

        EvalExperimentEntity entity = new EvalExperimentEntity();
        entity.setId(UUID.randomUUID());
        entity.setName(name != null ? name.trim() : "Experiment");
        entity.setDatasetId(datasetId);
        entity.setBaselineDefinitionId(baselineDefinitionId);
        entity.setVariantDefinitionId(variantDefinitionId);
        entity.setStatus(EvalExperimentStatus.DRAFT);
        entity.setCreatedBy(createdBy);
        entity.setCreatedAt(Instant.now());
        return toGraphql(experimentRepository.save(entity));
    }

    @Transactional
    public EvalExperiment run(UUID experimentId) {
        EvalExperimentEntity entity = requireExperiment(experimentId);
        entity.setStatus(EvalExperimentStatus.RUNNING);
        experimentRepository.save(entity);

        List<UUID> taskIds = datasetService.taskIds(entity.getDatasetId());
        resultRepository.deleteAll(resultRepository.findByExperimentIdOrderByCreatedAtAsc(experimentId));

        try {
            for (UUID taskId : taskIds) {
                EvalRunEntity baseline = runnerService.runSingleDefinition(
                        taskId, entity.getBaselineDefinitionId(), EvalTrigger.BATCH, null);
                saveResult(experimentId, taskId, "BASELINE", baseline);

                EvalRunEntity variant = runnerService.runSingleDefinition(
                        taskId, entity.getVariantDefinitionId(), EvalTrigger.BATCH, null);
                saveResult(experimentId, taskId, "VARIANT", variant);
            }
            entity.setStatus(EvalExperimentStatus.COMPLETE);
            entity.setCompletedAt(Instant.now());
        } catch (Exception ex) {
            entity.setStatus(EvalExperimentStatus.FAILED);
            entity.setNotes(ex.getMessage());
        }
        return toGraphql(experimentRepository.save(entity));
    }

    private void saveResult(UUID experimentId, UUID taskId, String variant, EvalRunEntity run) {
        EvalExperimentResultEntity result = new EvalExperimentResultEntity();
        result.setId(UUID.randomUUID());
        result.setExperimentId(experimentId);
        result.setTaskId(taskId);
        result.setVariant(variant);
        result.setEvalRunId(run != null ? run.getId() : null);
        if (run != null) {
            result.setScore(run.getScore());
            result.setLabel(run.getLabel() != null ? run.getLabel().name() : null);
        }
        result.setCreatedAt(Instant.now());
        resultRepository.save(result);
    }

    private EvalExperiment toGraphql(EvalExperimentEntity entity) {
        List<EvalExperimentResultEntity> results = resultRepository.findByExperimentIdOrderByCreatedAtAsc(entity.getId());
        return new EvalExperiment(
                entity.getId(),
                entity.getName(),
                entity.getDatasetId(),
                entity.getBaselineDefinitionId(),
                entity.getVariantDefinitionId(),
                entity.getStatus(),
                entity.getNotes(),
                entity.getCreatedBy(),
                entity.getCreatedAt(),
                entity.getCompletedAt(),
                buildComparison(results)
        );
    }

    private EvalExperimentComparison buildComparison(List<EvalExperimentResultEntity> results) {
        if (results.isEmpty()) {
            return new EvalExperimentComparison(0, 0, 0, 0, 0);
        }
        int tasks = (int) results.stream().map(EvalExperimentResultEntity::getTaskId).distinct().count();
        double baselinePass = passRate(results, "BASELINE");
        double variantPass = passRate(results, "VARIANT");
        double baselineScore = avgScore(results, "BASELINE");
        double variantScore = avgScore(results, "VARIANT");
        return new EvalExperimentComparison(tasks, baselinePass, variantPass, baselineScore, variantScore);
    }

    private static double passRate(List<EvalExperimentResultEntity> results, String variant) {
        List<EvalExperimentResultEntity> filtered = results.stream()
                .filter(r -> variant.equals(r.getVariant()))
                .toList();
        if (filtered.isEmpty()) {
            return 0;
        }
        long pass = filtered.stream().filter(r -> EvalRunLabel.PASS.name().equals(r.getLabel())).count();
        return (double) pass / filtered.size();
    }

    private static double avgScore(List<EvalExperimentResultEntity> results, String variant) {
        return results.stream()
                .filter(r -> variant.equals(r.getVariant()) && r.getScore() != null)
                .mapToDouble(EvalExperimentResultEntity::getScore)
                .average()
                .orElse(0);
    }

    private EvalExperimentResult toResultGraphql(EvalExperimentResultEntity entity) {
        return new EvalExperimentResult(
                entity.getId(),
                entity.getExperimentId(),
                entity.getTaskId(),
                entity.getVariant(),
                entity.getEvalRunId(),
                entity.getScore(),
                entity.getLabel(),
                entity.getCreatedAt()
        );
    }

    private EvalDefinitionEntity requireDefinition(UUID id) {
        return definitionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Eval definition not found"));
    }

    private EvalExperimentEntity requireExperiment(UUID id) {
        return experimentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Experiment not found"));
    }
}

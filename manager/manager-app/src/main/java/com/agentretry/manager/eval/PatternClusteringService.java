/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.eval;

import com.agentretry.manager.config.PatternsProperties;
import com.agentretry.manager.domain.TaskEntity;
import com.agentretry.manager.domain.TopicPatternEntity;
import com.agentretry.manager.eval.EvalRunLabel;
import com.agentretry.manager.eval.EvalRunStatus;
import com.agentretry.manager.graphql.TopicPattern;
import com.agentretry.manager.repository.EvalRunRepository;
import com.agentretry.manager.repository.TaskRepository;
import com.agentretry.manager.repository.TaskSpecifications;
import com.agentretry.manager.repository.TopicPatternRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PatternClusteringService {
    private final TopicPatternRepository patternRepository;
    private final TaskRepository taskRepository;
    private final EvalRunRepository evalRunRepository;
    private final PatternRelevanceClassifier relevanceClassifier;
    private final ObjectMapper objectMapper;
    private final PatternsProperties patternsProperties;

    public PatternClusteringService(
            TopicPatternRepository patternRepository,
            TaskRepository taskRepository,
            EvalRunRepository evalRunRepository,
            PatternRelevanceClassifier relevanceClassifier,
            ObjectMapper objectMapper,
            PatternsProperties patternsProperties
    ) {
        this.patternRepository = patternRepository;
        this.taskRepository = taskRepository;
        this.evalRunRepository = evalRunRepository;
        this.relevanceClassifier = relevanceClassifier;
        this.objectMapper = objectMapper;
        this.patternsProperties = patternsProperties;
    }

    @Transactional(readOnly = true)
    public List<TopicPattern> latestPatterns() {
        return patternRepository.findTop20ByOrderByComputedAtDescTaskCountDesc().stream()
                .map(this::toGraphql)
                .toList();
    }

    @Scheduled(cron = "${manager.patterns.schedule-cron:0 0 4 * * *}")
    @Transactional
    public void recomputePatternsScheduled() {
        recomputePatterns(patternsProperties.getDefaultWindowDays());
    }

    @Transactional
    public List<TopicPattern> recomputeNow(Integer windowDays) {
        int days = windowDays == null
                ? patternsProperties.getDefaultWindowDays()
                : patternsProperties.clampWindowDays(windowDays);
        recomputePatterns(days);
        return latestPatterns();
    }

    @Transactional
    public void recomputePatterns(int windowDays) {
        int days = patternsProperties.clampWindowDays(windowDays);
        Instant to = Instant.now();
        Instant from = to.minus(days, ChronoUnit.DAYS);
        List<TaskEntity> tasks = taskRepository.findAll(TaskSpecifications.filtered(
                null, null, from, to, null, null, null, null, null
        ));
        if (tasks.isEmpty()) {
            patternRepository.deleteAll(patternRepository.findAll());
            return;
        }

        Map<String, List<TaskEntity>> grouped = new HashMap<>();
        for (TaskEntity task : tasks) {
            String key = topicKey(task.getDescription());
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(task);
        }

        patternRepository.deleteAll(patternRepository.findAll());

        Instant computedAt = Instant.now();
        grouped.entrySet().stream()
                .filter(e -> e.getValue().size() >= 2)
                .sorted(Comparator.comparingInt((Map.Entry<String, List<TaskEntity>> e) -> e.getValue().size()).reversed())
                .limit(50)
                .forEach(entry -> {
                    List<TaskEntity> group = entry.getValue();
                    double failRate = computeFailRate(group);
                    List<UUID> samples = group.stream().limit(5).map(TaskEntity::getId).toList();
                    List<String> sampleDescriptions = group.stream()
                            .limit(5)
                            .map(TaskEntity::getDescription)
                            .toList();
                    PatternRelevanceClassifier.Assessment assessment = relevanceClassifier.assess(
                            entry.getKey(),
                            sampleDescriptions
                    );
                    TopicPatternEntity entity = new TopicPatternEntity();
                    entity.setId(UUID.randomUUID());
                    entity.setLabel(entry.getKey());
                    entity.setTaskCount(group.size());
                    entity.setFailRate(failRate);
                    try {
                        entity.setSampleTaskIds(objectMapper.writeValueAsString(samples));
                    } catch (Exception ignored) {
                        entity.setSampleTaskIds("[]");
                    }
                    entity.setPeriodStart(from);
                    entity.setPeriodEnd(to);
                    entity.setWindowDays(days);
                    entity.setComputedAt(computedAt);
                    entity.setRelevanceScore(assessment.relevanceScore());
                    entity.setSuspicious(assessment.suspicious());
                    entity.setFlagReason(assessment.flagReason());
                    patternRepository.save(entity);
                });
    }

    private double computeFailRate(List<TaskEntity> tasks) {
        int fails = 0;
        int scored = 0;
        for (TaskEntity task : tasks) {
            var runs = evalRunRepository.findByTaskIdOrderByCreatedAtDesc(task.getId()).stream()
                    .filter(r -> r.getStatus() == EvalRunStatus.COMPLETE && r.getLabel() != null)
                    .toList();
            if (runs.isEmpty()) {
                continue;
            }
            scored++;
            if (runs.getFirst().getLabel() == EvalRunLabel.FAIL) {
                fails++;
            }
        }
        return scored == 0 ? 0 : (double) fails / scored;
    }

    private static String topicKey(String description) {
        if (description == null || description.isBlank()) {
            return "unknown";
        }
        String[] words = description.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .trim()
                .split("\\s+");
        int limit = Math.min(4, words.length);
        return java.util.Arrays.stream(words, 0, limit).collect(Collectors.joining(" "));
    }

    private TopicPattern toGraphql(TopicPatternEntity entity) {
        List<UUID> samples = List.of();
        try {
            if (entity.getSampleTaskIds() != null) {
                samples = objectMapper.readValue(entity.getSampleTaskIds(), new TypeReference<>() {});
            }
        } catch (Exception ignored) {
        }
        return new TopicPattern(
                entity.getId(),
                entity.getLabel(),
                entity.getTaskCount(),
                entity.getFailRate(),
                samples,
                entity.getPeriodStart(),
                entity.getPeriodEnd(),
                entity.getComputedAt(),
                entity.getRelevanceScore(),
                entity.isSuspicious(),
                entity.getFlagReason(),
                entity.getWindowDays()
        );
    }
}

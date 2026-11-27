/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.eval;

import com.agentretry.manager.domain.EvalDefinitionEntity;
import com.agentretry.manager.domain.EvalRunEntity;
import com.agentretry.manager.eval.EvalRunLabel;
import com.agentretry.manager.eval.EvalRunStatus;
import com.agentretry.manager.graphql.EvalDefinition;
import com.agentretry.manager.graphql.EvalRun;
import com.agentretry.manager.graphql.EvalSummary;
import com.agentretry.manager.graphql.TimeSeriesPoint;
import com.agentretry.manager.repository.EvalDefinitionRepository;
import com.agentretry.manager.repository.EvalRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class EvalAnalyticsService {
    private final EvalRunRepository runRepository;
    private final EvalDefinitionRepository definitionRepository;

    public EvalAnalyticsService(EvalRunRepository runRepository, EvalDefinitionRepository definitionRepository) {
        this.runRepository = runRepository;
        this.definitionRepository = definitionRepository;
    }

    @Transactional(readOnly = true)
    public List<EvalRun> listRuns(UUID taskId) {
        Map<UUID, EvalDefinitionEntity> definitions = definitionRepository.findAll().stream()
                .collect(Collectors.toMap(EvalDefinitionEntity::getId, d -> d));
        return runRepository.findByTaskIdOrderByCreatedAtDesc(taskId).stream()
                .map(run -> toGraphql(run, definitions.get(run.getDefinitionId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public EvalSummary summary(Instant from, Instant to) {
        List<EvalRunEntity> runs = runRepository.findCompletedInRange(from, to, EvalRunStatus.COMPLETE);
        int pass = 0;
        int fail = 0;
        int warn = 0;
        for (EvalRunEntity run : runs) {
            if (run.getLabel() == EvalRunLabel.PASS) {
                pass++;
            } else if (run.getLabel() == EvalRunLabel.FAIL) {
                fail++;
            } else if (run.getLabel() == EvalRunLabel.WARN) {
                warn++;
            }
        }
        int total = pass + fail + warn;
        double passRate = total == 0 ? 0 : (double) pass / total;
        return new EvalSummary(total, pass, fail, warn, passRate);
    }

    @Transactional(readOnly = true)
    public List<TimeSeriesPoint> passRateTrend(Instant from, Instant to) {
        List<EvalRunEntity> runs = runRepository.findCompletedInRange(from, to, EvalRunStatus.COMPLETE);
        Map<LocalDate, int[]> byDay = new HashMap<>();
        for (EvalRunEntity run : runs) {
            LocalDate day = run.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate();
            int[] counts = byDay.computeIfAbsent(day, ignored -> new int[2]);
            counts[1]++;
            if (run.getLabel() == EvalRunLabel.PASS) {
                counts[0]++;
            }
        }
        List<TimeSeriesPoint> points = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE;
        byDay.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    int pass = entry.getValue()[0];
                    int total = entry.getValue()[1];
                    double rate = total == 0 ? 0 : (double) pass / total;
                    points.add(new TimeSeriesPoint(entry.getKey().format(fmt), rate));
                });
        return points;
    }

    static EvalRun toGraphql(EvalRunEntity run, EvalDefinitionEntity definition) {
        EvalDefinition defGraphql = definition == null ? null : new EvalDefinition(
                definition.getId(),
                definition.getSlug(),
                definition.getName(),
                definition.getKind(),
                definition.getPromptTemplate(),
                definition.getRuleConfig(),
                definition.getTargetScope(),
                definition.isEnabled(),
                definition.getCreatedAt(),
                definition.getUpdatedAt()
        );
        return new EvalRun(
                run.getId(),
                run.getDefinitionId(),
                defGraphql,
                run.getTaskId(),
                run.getEventId(),
                run.getScore(),
                run.getLabel(),
                run.getExplanation(),
                run.getStatus(),
                run.getTriggeredBy(),
                run.getCreatedAt()
        );
    }
}

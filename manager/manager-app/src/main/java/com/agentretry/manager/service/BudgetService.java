/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.service;

import com.agentretry.manager.domain.BudgetPeriod;
import com.agentretry.manager.domain.BudgetScope;
import com.agentretry.manager.domain.CostAlertEntity;
import com.agentretry.manager.domain.CostBudgetEntity;
import com.agentretry.manager.graphql.CostAlert;
import com.agentretry.manager.graphql.CostBudget;
import com.agentretry.manager.repository.CostAlertRepository;
import com.agentretry.manager.repository.CostAnalyticsRepository;
import com.agentretry.manager.repository.CostBudgetRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BudgetService {

    private final CostBudgetRepository costBudgetRepository;
    private final CostAlertRepository costAlertRepository;
    private final CostAnalyticsRepository costAnalyticsRepository;
    private final AlertChannelService alertChannelService;

    public BudgetService(
            CostBudgetRepository costBudgetRepository,
            CostAlertRepository costAlertRepository,
            CostAnalyticsRepository costAnalyticsRepository,
            AlertChannelService alertChannelService
    ) {
        this.costBudgetRepository = costBudgetRepository;
        this.costAlertRepository = costAlertRepository;
        this.costAnalyticsRepository = costAnalyticsRepository;
        this.alertChannelService = alertChannelService;
    }

    @Transactional(readOnly = true)
    public List<CostBudget> listBudgets() {
        evaluateAllBudgets();
        return costBudgetRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(this::toGraphql)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CostBudget> listAllBudgets() {
        evaluateAllBudgets();
        return costBudgetRepository.findAll().stream()
                .map(this::toGraphql)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CostAlert> listActiveAlerts() {
        evaluateAllBudgets();
        return costAlertRepository.findByResolvedAtIsNullOrderByTriggeredAtDesc().stream()
                .map(this::toAlertGraphql)
                .toList();
    }

    @Transactional
    public CostBudget createBudget(
            String name,
            BudgetScope scope,
            String scopeValue,
            BudgetPeriod period,
            BigDecimal limitUsd,
            BigDecimal alertThresholdPct,
            boolean active
    ) {
        validateBudgetInput(name, scope, scopeValue, limitUsd, alertThresholdPct);

        Instant now = Instant.now();
        CostBudgetEntity entity = new CostBudgetEntity();
        entity.setId(UUID.randomUUID());
        entity.setName(name.trim());
        entity.setScope(scope);
        entity.setScopeValue(normalizeScopeValue(scope, scopeValue));
        entity.setPeriod(period);
        entity.setLimitUsd(limitUsd.setScale(6, RoundingMode.HALF_UP));
        entity.setAlertThresholdPct(alertThresholdPct != null
                ? alertThresholdPct.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(80));
        entity.setActive(active);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        costBudgetRepository.save(entity);
        evaluateBudget(entity);
        return toGraphql(entity);
    }

    @Transactional
    public CostBudget updateBudget(
            UUID id,
            String name,
            BudgetScope scope,
            String scopeValue,
            BudgetPeriod period,
            BigDecimal limitUsd,
            BigDecimal alertThresholdPct,
            Boolean active
    ) {
        CostBudgetEntity entity = costBudgetRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Budget not found: " + id));

        if (name != null && !name.isBlank()) {
            entity.setName(name.trim());
        }
        if (scope != null) {
            entity.setScope(scope);
        }
        if (scopeValue != null || scope != null) {
            entity.setScopeValue(normalizeScopeValue(entity.getScope(), scopeValue));
        }
        if (period != null) {
            entity.setPeriod(period);
        }
        if (limitUsd != null) {
            validateLimitUsd(limitUsd);
            entity.setLimitUsd(limitUsd.setScale(6, RoundingMode.HALF_UP));
        }
        if (alertThresholdPct != null) {
            validateAlertThreshold(alertThresholdPct);
            entity.setAlertThresholdPct(alertThresholdPct.setScale(2, RoundingMode.HALF_UP));
        }
        if (active != null) {
            entity.setActive(active);
        }
        entity.setUpdatedAt(Instant.now());
        costBudgetRepository.save(entity);
        evaluateBudget(entity);
        return toGraphql(entity);
    }

    @Transactional
    public boolean deleteBudget(UUID id) {
        if (!costBudgetRepository.existsById(id)) {
            return false;
        }
        costBudgetRepository.deleteById(id);
        return true;
    }

    @Transactional
    public void evaluateAllBudgets() {
        for (CostBudgetEntity budget : costBudgetRepository.findByActiveTrueOrderByNameAsc()) {
            evaluateBudget(budget);
        }
    }

    private void evaluateBudget(CostBudgetEntity budget) {
        if (!budget.isActive()) {
            return;
        }

        PeriodRange range = currentPeriodRange(budget.getPeriod());
        double spend = costAnalyticsRepository.sumCostForScope(
                range.from(),
                range.to(),
                budget.getScope().name(),
                budget.getScopeValue()
        );

        BigDecimal spendUsd = BigDecimal.valueOf(spend).setScale(6, RoundingMode.HALF_UP);
        BigDecimal limitUsd = budget.getLimitUsd();
        BigDecimal thresholdSpend = limitUsd.multiply(budget.getAlertThresholdPct())
                .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);

        List<CostAlertEntity> activeAlerts = costAlertRepository.findByBudgetIdAndResolvedAtIsNull(budget.getId());
        if (spendUsd.compareTo(thresholdSpend) >= 0) {
            if (activeAlerts.isEmpty()) {
                CostAlertEntity alert = new CostAlertEntity();
                alert.setId(UUID.randomUUID());
                alert.setBudget(budget);
                alert.setTriggeredAt(Instant.now());
                alert.setSpendUsd(spendUsd);
                alert.setLimitUsd(limitUsd);
                alert.setMessage(buildAlertMessage(budget, spendUsd, limitUsd));
                costAlertRepository.save(alert);
                alertChannelService.notifyChannels(
                        alert.getMessage(),
                        Map.of(
                                "budgetId", budget.getId().toString(),
                                "spendUsd", spendUsd,
                                "limitUsd", limitUsd
                        )
                );
            } else {
                CostAlertEntity alert = activeAlerts.getFirst();
                alert.setSpendUsd(spendUsd);
                alert.setLimitUsd(limitUsd);
                alert.setMessage(buildAlertMessage(budget, spendUsd, limitUsd));
                costAlertRepository.save(alert);
            }
        } else {
            for (CostAlertEntity alert : activeAlerts) {
                alert.setResolvedAt(Instant.now());
                costAlertRepository.save(alert);
            }
        }
    }

    private CostBudget toGraphql(CostBudgetEntity entity) {
        PeriodRange range = currentPeriodRange(entity.getPeriod());
        double spend = costAnalyticsRepository.sumCostForScope(
                range.from(),
                range.to(),
                entity.getScope().name(),
                entity.getScopeValue()
        );
        return new CostBudget(
                entity.getId(),
                entity.getName(),
                entity.getScope(),
                entity.getScopeValue(),
                entity.getPeriod(),
                entity.getLimitUsd(),
                entity.getAlertThresholdPct(),
                entity.isActive(),
                BigDecimal.valueOf(spend).setScale(6, RoundingMode.HALF_UP),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private CostAlert toAlertGraphql(CostAlertEntity entity) {
        return new CostAlert(
                entity.getId(),
                entity.getBudget().getId(),
                entity.getBudget().getName(),
                entity.getTriggeredAt(),
                entity.getSpendUsd(),
                entity.getLimitUsd(),
                entity.getMessage()
        );
    }

    private static String buildAlertMessage(CostBudgetEntity budget, BigDecimal spendUsd, BigDecimal limitUsd) {
        return budget.getName() + ": spend $" + spendUsd + " exceeds threshold (limit $" + limitUsd + ")";
    }

    private static PeriodRange currentPeriodRange(BudgetPeriod period) {
        Instant now = Instant.now();
        var zoned = now.atZone(ZoneOffset.UTC);
        return switch (period) {
            case WEEKLY -> {
                var start = zoned.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        .toLocalDate()
                        .atStartOfDay(ZoneOffset.UTC)
                        .toInstant();
                yield new PeriodRange(start, now);
            }
            case MONTHLY -> {
                var start = zoned.withDayOfMonth(1).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
                yield new PeriodRange(start, now);
            }
        };
    }

    private static void validateBudgetInput(
            String name,
            BudgetScope scope,
            String scopeValue,
            BigDecimal limitUsd,
            BigDecimal alertThresholdPct
    ) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Budget name is required");
        }
        if (scope == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Budget scope is required");
        }
        if (scope != BudgetScope.ORG && (scopeValue == null || scopeValue.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scopeValue is required for " + scope);
        }
        validateLimitUsd(limitUsd);
        if (alertThresholdPct != null) {
            validateAlertThreshold(alertThresholdPct);
        }
    }

    private static void validateLimitUsd(BigDecimal limitUsd) {
        if (limitUsd == null || limitUsd.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limitUsd must be positive");
        }
    }

    private static void validateAlertThreshold(BigDecimal alertThresholdPct) {
        if (alertThresholdPct.signum() <= 0 || alertThresholdPct.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "alertThresholdPct must be between 0 and 100");
        }
    }

    private static String normalizeScopeValue(BudgetScope scope, String scopeValue) {
        if (scope == BudgetScope.ORG) {
            return null;
        }
        if (scopeValue == null || scopeValue.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scopeValue is required for " + scope);
        }
        return scopeValue.trim();
    }

    private record PeriodRange(Instant from, Instant to) {
    }
}

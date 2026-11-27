/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.graphql;

import com.agentretry.contract.SourceTool;
import com.agentretry.manager.config.CostFeatureProperties;
import com.agentretry.manager.domain.BudgetPeriod;
import com.agentretry.manager.domain.BudgetScope;
import com.agentretry.manager.security.SecadminGuard;
import com.agentretry.manager.service.BillingReconciliationService;
import com.agentretry.manager.service.BudgetService;
import com.agentretry.manager.service.CostAnalyticsService;
import com.agentretry.manager.service.RetryAnalyticsService;
import com.agentretry.manager.platform.FeatureGateService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Controller
public class CostGraphqlController {

    private final CostAnalyticsService costAnalyticsService;
    private final RetryAnalyticsService retryAnalyticsService;
    private final BudgetService budgetService;
    private final BillingReconciliationService billingReconciliationService;
    private final CostFeatureProperties costFeatureProperties;
    private final FeatureGateService featureGateService;

    public CostGraphqlController(
            CostAnalyticsService costAnalyticsService,
            RetryAnalyticsService retryAnalyticsService,
            BudgetService budgetService,
            BillingReconciliationService billingReconciliationService,
            CostFeatureProperties costFeatureProperties,
            FeatureGateService featureGateService
    ) {
        this.costAnalyticsService = costAnalyticsService;
        this.retryAnalyticsService = retryAnalyticsService;
        this.budgetService = budgetService;
        this.billingReconciliationService = billingReconciliationService;
        this.costFeatureProperties = costFeatureProperties;
        this.featureGateService = featureGateService;
    }

    @QueryMapping
    public CostSummary costSummary(
            @Argument Instant from,
            @Argument Instant to,
            @Argument String user,
            @Argument SourceTool sourceTool
    ) {
        if (!featureGateService.isCostEnabled()) {
            return CostSummary.disabled();
        }
        return costAnalyticsService.costSummary(from, to, user, sourceTool);
    }

    @QueryMapping
    public RetryAnalysisSummary retryAnalysis(
            @Argument Instant from,
            @Argument Instant to,
            @Argument String user,
            @Argument SourceTool sourceTool
    ) {
        if (!featureGateService.isCostEnabled()) {
            return RetryAnalysisSummary.empty();
        }
        return retryAnalyticsService.retryAnalysis(from, to, user, sourceTool);
    }

    @QueryMapping
    public List<CostBudget> costBudgets() {
        if (!featureGateService.isCostEnabled()) {
            return List.of();
        }
        return budgetService.listAllBudgets();
    }

    @QueryMapping
    public List<CostAlert> costAlerts() {
        if (!featureGateService.isCostEnabled()) {
            return List.of();
        }
        return budgetService.listActiveAlerts();
    }

    @QueryMapping
    public CostFeatures costFeatures() {
        if (!featureGateService.isCostEnabled()) {
            return new CostFeatures(false, false, false);
        }
        return new CostFeatures(
                costFeatureProperties.isEnterpriseEnabled(),
                costFeatureProperties.isBillingApiEnabled(),
                costFeatureProperties.isBillingReconciliationAvailable()
        );
    }

    @QueryMapping
    public BillingReconciliation billingReconciliation(@Argument Instant from, @Argument Instant to) {
        if (!featureGateService.isCostEnabled()) {
            return null;
        }
        if (!costFeatureProperties.isBillingReconciliationAvailable()) {
            return null;
        }
        return billingReconciliationService.reconcile(from, to);
    }

    @MutationMapping
    public CostBudget createCostBudget(
            Authentication authentication,
            @Argument String name,
            @Argument BudgetScope scope,
            @Argument String scopeValue,
            @Argument BudgetPeriod period,
            @Argument BigDecimal limitUsd,
            @Argument BigDecimal alertThresholdPct,
            @Argument Boolean active
    ) {
        if (!featureGateService.isCostEnabled()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "Cost analytics is not enabled for this installation"
            );
        }
        SecadminGuard.requireSecadmin(authentication);
        return budgetService.createBudget(
                name,
                scope,
                scopeValue,
                period,
                limitUsd,
                alertThresholdPct,
                active == null || active
        );
    }

    @MutationMapping
    public CostBudget updateCostBudget(
            Authentication authentication,
            @Argument UUID id,
            @Argument String name,
            @Argument BudgetScope scope,
            @Argument String scopeValue,
            @Argument BudgetPeriod period,
            @Argument BigDecimal limitUsd,
            @Argument BigDecimal alertThresholdPct,
            @Argument Boolean active
    ) {
        SecadminGuard.requireSecadmin(authentication);
        return budgetService.updateBudget(id, name, scope, scopeValue, period, limitUsd, alertThresholdPct, active);
    }

    @MutationMapping
    public boolean deleteCostBudget(Authentication authentication, @Argument UUID id) {
        SecadminGuard.requireSecadmin(authentication);
        return budgetService.deleteBudget(id);
    }

    @MutationMapping
    public BillingActual importBillingActual(
            Authentication authentication,
            @Argument Instant periodStart,
            @Argument Instant periodEnd,
            @Argument BigDecimal actualCostUsd,
            @Argument String notes
    ) {
        String email = SecadminGuard.requireSecadmin(authentication);
        return billingReconciliationService.importManualActual(
                periodStart,
                periodEnd,
                actualCostUsd,
                notes,
                email
        );
    }

    @MutationMapping
    public BillingActual fetchCursorBillingActual(
            Authentication authentication,
            @Argument Instant periodStart,
            @Argument Instant periodEnd
    ) {
        String email = SecadminGuard.requireSecadmin(authentication);
        return billingReconciliationService.fetchFromCursorApi(periodStart, periodEnd, email);
    }
}

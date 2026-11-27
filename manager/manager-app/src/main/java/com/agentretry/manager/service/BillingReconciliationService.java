/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.service;

import com.agentretry.manager.config.CostFeatureProperties;
import com.agentretry.manager.domain.BillingActualEntity;
import com.agentretry.manager.domain.BillingActualSource;
import com.agentretry.manager.graphql.BillingActual;
import com.agentretry.manager.graphql.BillingReconciliation;
import com.agentretry.manager.repository.BillingActualRepository;
import com.agentretry.manager.repository.CostAnalyticsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BillingReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(BillingReconciliationService.class);
    private static final String DEFAULT_CURSOR_SPEND_URL = "https://api.cursor.com/teams/spend";

    private final CostFeatureProperties costFeatureProperties;
    private final BillingActualRepository billingActualRepository;
    private final CostAnalyticsRepository costAnalyticsRepository;
    private final RestClient restClient;

    public BillingReconciliationService(
            CostFeatureProperties costFeatureProperties,
            BillingActualRepository billingActualRepository,
            CostAnalyticsRepository costAnalyticsRepository
    ) {
        this.costFeatureProperties = costFeatureProperties;
        this.billingActualRepository = billingActualRepository;
        this.costAnalyticsRepository = costAnalyticsRepository;
        this.restClient = RestClient.create();
    }

    @Transactional(readOnly = true)
    public BillingReconciliation reconcile(Instant from, Instant to) {
        if (!costFeatureProperties.isEnterpriseEnabled()) {
            return null;
        }

        Instant resolvedFrom = from != null ? from : Instant.now().minusSeconds(7 * 24 * 3600L);
        Instant resolvedTo = to != null ? to : Instant.now();

        double estimatedCostUsd = costAnalyticsRepository.sumTotalCost(resolvedFrom, resolvedTo, null, null);
        List<BillingActualEntity> actualEntities = billingActualRepository.findByPeriodRange(resolvedFrom, resolvedTo);
        double actualCostUsd = actualEntities.stream()
                .map(BillingActualEntity::getActualCostUsd)
                .mapToDouble(BigDecimal::doubleValue)
                .sum();

        double varianceUsd = actualCostUsd - estimatedCostUsd;
        double variancePct = actualCostUsd > 0 ? (varianceUsd / actualCostUsd) * 100 : 0;

        List<BillingActual> actuals = actualEntities.stream().map(this::toGraphql).toList();
        return new BillingReconciliation(estimatedCostUsd, actualCostUsd, varianceUsd, variancePct, actuals);
    }

    @Transactional
    public BillingActual importManualActual(
            Instant periodStart,
            Instant periodEnd,
            BigDecimal actualCostUsd,
            String notes,
            String importedBy
    ) {
        requireEnterpriseEnabled();

        if (periodStart == null || periodEnd == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "periodStart and periodEnd are required");
        }
        if (periodStart.isAfter(periodEnd)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "periodStart must be before periodEnd");
        }
        if (actualCostUsd == null || actualCostUsd.signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "actualCostUsd must be non-negative");
        }

        BillingActualEntity entity = new BillingActualEntity();
        entity.setId(UUID.randomUUID());
        entity.setPeriodStart(periodStart);
        entity.setPeriodEnd(periodEnd);
        entity.setActualCostUsd(actualCostUsd.setScale(6, RoundingMode.HALF_UP));
        entity.setSource(BillingActualSource.MANUAL);
        entity.setNotes(notes);
        entity.setImportedAt(Instant.now());
        entity.setImportedBy(importedBy);
        billingActualRepository.save(entity);
        return toGraphql(entity);
    }

    @Transactional
    public BillingActual fetchFromCursorApi(Instant periodStart, Instant periodEnd, String importedBy) {
        requireEnterpriseEnabled();
        if (!costFeatureProperties.isBillingApiEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Billing API is disabled");
        }

        String apiKey = costFeatureProperties.getCursorBillingApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cursor billing API key is not configured");
        }

        String apiUrl = costFeatureProperties.getCursorBillingApiUrl();
        if (apiUrl == null || apiUrl.isBlank()) {
            apiUrl = DEFAULT_CURSOR_SPEND_URL;
        }

        try {
            double actualCost = callCursorSpendApi(apiUrl, apiKey);

            BillingActualEntity entity = new BillingActualEntity();
            entity.setId(UUID.randomUUID());
            entity.setPeriodStart(periodStart != null ? periodStart : Instant.now().minusSeconds(30L * 24 * 3600));
            entity.setPeriodEnd(periodEnd != null ? periodEnd : Instant.now());
            entity.setActualCostUsd(BigDecimal.valueOf(actualCost).setScale(6, RoundingMode.HALF_UP));
            entity.setSource(BillingActualSource.CURSOR_API);
            entity.setNotes("Imported from Cursor Admin API (/teams/spend)");
            entity.setImportedAt(Instant.now());
            entity.setImportedBy(importedBy);
            billingActualRepository.save(entity);
            return toGraphql(entity);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Failed to fetch Cursor billing API: {}", ex.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch Cursor billing data");
        }
    }

    @SuppressWarnings("unchecked")
    private double callCursorSpendApi(String apiUrl, String apiKey) {
        String basic = Base64.getEncoder().encodeToString((apiKey + ":").getBytes(StandardCharsets.UTF_8));
        Map<String, Object> body = new HashMap<>();
        body.put("page", 1);
        body.put("pageSize", 100);

        Map<String, Object> response = restClient.post()
                .uri(apiUrl)
                .header("Authorization", "Basic " + basic)
                .header("Content-Type", "application/json")
                .body(body)
                .retrieve()
                .body(Map.class);

        if (response == null) {
            return 0;
        }

        // Prefer team-level overallSpendCents (Admin API), fall back to legacy fields.
        Object overall = response.get("overallSpendCents");
        if (overall == null) {
            overall = response.get("totalSpendCents");
        }
        if (overall instanceof Number number) {
            return number.doubleValue() / 100.0;
        }

        Object total = response.get("totalCostUsd");
        if (total == null) {
            total = response.get("total_cost_usd");
        }
        if (total instanceof Number number) {
            return number.doubleValue();
        }

        Object teamMemberSpend = response.get("teamMemberSpend");
        if (teamMemberSpend instanceof List<?> members) {
            double sumCents = 0;
            for (Object member : members) {
                if (member instanceof Map<?, ?> map) {
                    Object spend = map.get("spendCents");
                    if (spend == null) {
                        spend = map.get("overallSpendCents");
                    }
                    if (spend instanceof Number number) {
                        sumCents += number.doubleValue();
                    }
                }
            }
            return sumCents / 100.0;
        }

        return 0;
    }

    private void requireEnterpriseEnabled() {
        if (!costFeatureProperties.isEnterpriseEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Enterprise cost features are disabled");
        }
    }

    private BillingActual toGraphql(BillingActualEntity entity) {
        return new BillingActual(
                entity.getId(),
                entity.getPeriodStart(),
                entity.getPeriodEnd(),
                entity.getActualCostUsd(),
                entity.getSource(),
                entity.getNotes(),
                entity.getImportedAt(),
                entity.getImportedBy()
        );
    }
}

/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Phase-3 stub: reconcile Claude Cowork org Analytics API totals against OTel-ingested sessions.
 * Enable with {@code manager.cowork.analytics-api-enabled=true} and an API key when available.
 */
@Service
public class CoworkAnalyticsReconcileService {
    private static final Logger log = LoggerFactory.getLogger(CoworkAnalyticsReconcileService.class);

    private final boolean enabled;
    private final String apiUrl;
    private final String apiKey;

    public CoworkAnalyticsReconcileService(
            @Value("${manager.cowork.analytics-api-enabled:false}") boolean enabled,
            @Value("${manager.cowork.analytics-api-url:}") String apiUrl,
            @Value("${manager.cowork.analytics-api-key:}") String apiKey
    ) {
        this.enabled = enabled;
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
    }

    @Scheduled(cron = "${manager.cowork.analytics-reconcile-cron:0 15 3 * * *}")
    public void reconcile() {
        if (!enabled) {
            return;
        }
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Cowork analytics reconcile enabled but manager.cowork.analytics-api-key is empty");
            return;
        }
        // Placeholder until Anthropic Analytics API credentials are configured in deployment.
        log.info("Cowork analytics reconcile skipped (stub). url={}", apiUrl.isBlank() ? "(default)" : apiUrl);
    }
}

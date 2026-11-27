/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.clickhouse;

import com.agentretry.manager.config.AnalyticsProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class ClickHouseConfig {

    @Bean
    @ConditionalOnProperty(prefix = "manager.analytics.clickhouse", name = "enabled", havingValue = "true")
    public ClickHouseHttpClient clickHouseHttpClient(AnalyticsProperties analyticsProperties) {
        return new ClickHouseHttpClient(analyticsProperties.getClickhouse());
    }
}

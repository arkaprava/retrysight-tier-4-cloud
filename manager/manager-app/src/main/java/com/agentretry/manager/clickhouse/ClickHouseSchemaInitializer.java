/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.clickhouse;

import com.agentretry.manager.config.AnalyticsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "manager.analytics.clickhouse", name = "enabled", havingValue = "true")
public class ClickHouseSchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(ClickHouseSchemaInitializer.class);

    private final AnalyticsProperties.ClickHouse props;
    private final ClickHouseHttpClient client;

    public ClickHouseSchemaInitializer(AnalyticsProperties analyticsProperties, ClickHouseHttpClient client) {
        this.props = analyticsProperties.getClickhouse();
        this.client = client;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureSchema() {
        try {
            client.executeWithoutDatabase("CREATE DATABASE IF NOT EXISTS " + props.getDatabase());
            client.execute("""
                    CREATE TABLE IF NOT EXISTS token_usage_analytics (
                        id UUID,
                        task_id UUID,
                        agent_id String,
                        developer_email String,
                        source_tool LowCardinality(String),
                        turn_index Int32,
                        model LowCardinality(String),
                        input_tokens Int64,
                        output_tokens Int64,
                        cache_read_tokens Int64,
                        cache_write_tokens Int64,
                        reasoning_tokens Int64,
                        estimated_cost_usd Float64,
                        recorded_at DateTime64(3, 'UTC'),
                        idempotency_key String
                    ) ENGINE = ReplacingMergeTree(recorded_at)
                    PARTITION BY toYYYYMM(recorded_at)
                    ORDER BY (task_id, idempotency_key, recorded_at)
                    """);
            client.execute("""
                    CREATE TABLE IF NOT EXISTS task_events_analytics (
                        id UUID,
                        task_id UUID,
                        agent_id String,
                        developer_email String,
                        source_tool LowCardinality(String),
                        event_type LowCardinality(String),
                        is_retry UInt8,
                        sequence_num Int32,
                        occurred_at DateTime64(3, 'UTC'),
                        idempotency_key String
                    ) ENGINE = ReplacingMergeTree(occurred_at)
                    PARTITION BY toYYYYMM(occurred_at)
                    ORDER BY (task_id, idempotency_key, occurred_at)
                    """);
            log.info("ClickHouse analytics schema ready (database={})", props.getDatabase());
        } catch (Exception ex) {
            log.error("Failed to initialize ClickHouse schema — dual-write will retry on flush: {}",
                    ex.getMessage());
        }
    }
}

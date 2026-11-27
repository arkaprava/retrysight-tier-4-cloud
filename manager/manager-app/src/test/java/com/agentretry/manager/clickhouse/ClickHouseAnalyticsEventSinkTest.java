/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.clickhouse;

import com.agentretry.manager.config.AnalyticsProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ClickHouseAnalyticsEventSinkTest {

    @Test
    void serializesTokenUsageAsJsonEachRow() throws Exception {
        AnalyticsProperties props = new AnalyticsProperties();
        props.getClickhouse().setEnabled(true);
        ClickHouseAnalyticsEventSink sink = new ClickHouseAnalyticsEventSink(
                props,
                new ClickHouseHttpClient(props.getClickhouse()),
                new ObjectMapper()
        );

        UUID id = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        String json = sink.toTokenJsonLines(List.of(new TokenUsageAnalyticsRow(
                id,
                taskId,
                UUID.randomUUID(),
                "dev@example.com",
                "CURSOR",
                1,
                "gpt-4.1",
                10,
                20,
                0,
                0,
                0,
                new BigDecimal("0.01"),
                Instant.parse("2026-07-19T12:00:00Z"),
                "idem-1"
        )));

        assertTrue(json.contains("\"idempotency_key\":\"idem-1\""));
        assertTrue(json.contains("\"source_tool\":\"CURSOR\""));
        assertTrue(json.contains(id.toString()));
        assertTrue(json.contains("2026-07-19 12:00:00.000"));
    }
}

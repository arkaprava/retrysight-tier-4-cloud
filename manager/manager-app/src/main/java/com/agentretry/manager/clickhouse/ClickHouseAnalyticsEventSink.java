/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.clickhouse;

import com.agentretry.manager.config.AnalyticsProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(prefix = "manager.analytics.clickhouse", name = "enabled", havingValue = "true")
public class ClickHouseAnalyticsEventSink implements AnalyticsEventSink {

    private static final Logger log = LoggerFactory.getLogger(ClickHouseAnalyticsEventSink.class);
    private static final DateTimeFormatter CH_TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneOffset.UTC);

    private final AnalyticsProperties.ClickHouse props;
    private final ClickHouseHttpClient client;
    private final ObjectMapper objectMapper;
    private final ConcurrentLinkedQueue<TokenUsageAnalyticsRow> tokenQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<TaskEventAnalyticsRow> eventQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean flushing = new AtomicBoolean(false);

    public ClickHouseAnalyticsEventSink(
            AnalyticsProperties analyticsProperties,
            ClickHouseHttpClient clickHouseHttpClient,
            ObjectMapper objectMapper
    ) {
        this.props = analyticsProperties.getClickhouse();
        this.client = clickHouseHttpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void publishTokenUsage(TokenUsageAnalyticsRow row) {
        if (row != null) {
            tokenQueue.offer(row);
            maybeFlushBySize();
        }
    }

    @Override
    public void publishTaskEvent(TaskEventAnalyticsRow row) {
        if (row != null) {
            eventQueue.offer(row);
            maybeFlushBySize();
        }
    }

    @Scheduled(fixedDelayString = "${manager.analytics.clickhouse.flush-interval-ms:1000}")
    public void scheduledFlush() {
        flush();
    }

    @PreDestroy
    public void onShutdown() {
        flush();
    }

    @Override
    public void flush() {
        if (!flushing.compareAndSet(false, true)) {
            return;
        }
        try {
            flushTokens();
            flushEvents();
        } finally {
            flushing.set(false);
        }
    }

    private void maybeFlushBySize() {
        int batch = Math.max(1, props.getFlushBatchSize());
        if (tokenQueue.size() >= batch || eventQueue.size() >= batch) {
            flush();
        }
    }

    private void flushTokens() {
        List<TokenUsageAnalyticsRow> batch = drain(tokenQueue, props.getFlushBatchSize());
        if (batch.isEmpty()) {
            return;
        }
        try {
            client.insertJsonEachRow("token_usage_analytics", toTokenJsonLines(batch));
            log.debug("Flushed {} token_usage rows to ClickHouse", batch.size());
        } catch (Exception ex) {
            log.warn("ClickHouse token_usage flush failed ({} rows retained for retry): {}",
                    batch.size(), ex.getMessage());
            tokenQueue.addAll(batch);
        }
    }

    private void flushEvents() {
        List<TaskEventAnalyticsRow> batch = drain(eventQueue, props.getFlushBatchSize());
        if (batch.isEmpty()) {
            return;
        }
        try {
            client.insertJsonEachRow("task_events_analytics", toEventJsonLines(batch));
            log.debug("Flushed {} task_events rows to ClickHouse", batch.size());
        } catch (Exception ex) {
            log.warn("ClickHouse task_events flush failed ({} rows retained for retry): {}",
                    batch.size(), ex.getMessage());
            eventQueue.addAll(batch);
        }
    }

    private <T> List<T> drain(ConcurrentLinkedQueue<T> queue, int max) {
        List<T> batch = new ArrayList<>(Math.min(max, 256));
        T item;
        while (batch.size() < max && (item = queue.poll()) != null) {
            batch.add(item);
        }
        return batch;
    }

    String toTokenJsonLines(List<TokenUsageAnalyticsRow> rows) throws JsonProcessingException {
        StringBuilder sb = new StringBuilder();
        for (TokenUsageAnalyticsRow row : rows) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("id", row.id().toString());
            node.put("task_id", row.taskId().toString());
            node.put("agent_id", row.agentId() != null ? row.agentId().toString() : "");
            node.put("developer_email", nullToEmpty(row.developerEmail()));
            node.put("source_tool", nullToEmpty(row.sourceTool()));
            node.put("turn_index", row.turnIndex());
            node.put("model", nullToEmpty(row.model()));
            node.put("input_tokens", row.inputTokens());
            node.put("output_tokens", row.outputTokens());
            node.put("cache_read_tokens", row.cacheReadTokens());
            node.put("cache_write_tokens", row.cacheWriteTokens());
            node.put("reasoning_tokens", row.reasoningTokens());
            BigDecimal cost = row.estimatedCostUsd() != null ? row.estimatedCostUsd() : BigDecimal.ZERO;
            node.put("estimated_cost_usd", cost.doubleValue());
            node.put("recorded_at", CH_TS.format(row.recordedAt()));
            node.put("idempotency_key", nullToEmpty(row.idempotencyKey()));
            sb.append(objectMapper.writeValueAsString(node)).append('\n');
        }
        return sb.toString();
    }

    String toEventJsonLines(List<TaskEventAnalyticsRow> rows) throws JsonProcessingException {
        StringBuilder sb = new StringBuilder();
        for (TaskEventAnalyticsRow row : rows) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("id", row.id().toString());
            node.put("task_id", row.taskId().toString());
            node.put("agent_id", row.agentId() != null ? row.agentId().toString() : "");
            node.put("developer_email", nullToEmpty(row.developerEmail()));
            node.put("source_tool", nullToEmpty(row.sourceTool()));
            node.put("event_type", nullToEmpty(row.eventType()));
            node.put("is_retry", row.retry() ? 1 : 0);
            node.put("sequence_num", row.sequenceNum());
            node.put("occurred_at", CH_TS.format(row.occurredAt()));
            node.put("idempotency_key", nullToEmpty(row.idempotencyKey()));
            sb.append(objectMapper.writeValueAsString(node)).append('\n');
        }
        return sb.toString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

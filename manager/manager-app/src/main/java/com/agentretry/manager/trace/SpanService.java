/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.trace;

import com.agentretry.manager.domain.SpanEntity;
import com.agentretry.manager.graphql.SpanNode;
import com.agentretry.manager.graphql.TraceSpan;
import com.agentretry.manager.repository.SpanRepository;
import com.agentretry.manager.security.PayloadRedactionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SpanService {
    private final SpanRepository spanRepository;
    private final PayloadRedactionService redactionService;
    private final ObjectMapper objectMapper;

    public SpanService(
            SpanRepository spanRepository,
            PayloadRedactionService redactionService,
            ObjectMapper objectMapper
    ) {
        this.spanRepository = spanRepository;
        this.redactionService = redactionService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public int persistSpans(List<SpanEntity> spans) {
        int saved = 0;
        for (SpanEntity span : spans) {
            if (span.getExternalTraceId() != null && span.getExternalSpanId() != null
                    && spanRepository.existsByExternalTraceIdAndExternalSpanId(
                    span.getExternalTraceId(), span.getExternalSpanId())) {
                continue;
            }
            spanRepository.save(span);
            saved++;
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public List<SpanNode> spanTreeForTask(UUID taskId, Instant eventBase) {
        List<SpanEntity> spans = spanRepository.findByTaskIdOrderByStartAtAsc(taskId);
        if (spans.isEmpty()) {
            return List.of();
        }
        Instant base = spans.getFirst().getStartAt();
        Map<UUID, UUID> externalToInternal = new HashMap<>();
        for (SpanEntity span : spans) {
            externalToInternal.put(span.getId(), span.getId());
        }
        List<SpanNode> nodes = new ArrayList<>();
        for (SpanEntity span : spans) {
            long startMs = Math.max(0, span.getStartAt().toEpochMilli() - base.toEpochMilli());
            nodes.add(new SpanNode(
                    span.getId(),
                    span.getName(),
                    span.getKind() != null ? span.getKind() : "SPAN",
                    startMs,
                    Math.max(1, span.getDurationMs()),
                    span.getStatus() != null ? span.getStatus() : "ok",
                    span.getParentSpanId(),
                    span.getAttributes()
            ));
        }
        return nodes;
    }

    @Transactional(readOnly = true)
    public List<TraceSpan> listForTask(UUID taskId) {
        return spanRepository.findByTaskIdOrderByStartAtAsc(taskId).stream()
                .map(this::toGraphql)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TraceSpan> search(UUID taskId, String sourceTool, Instant from, Instant to) {
        return spanRepository.search(taskId, sourceTool, from, to).stream()
                .map(this::toGraphql)
                .toList();
    }

    public SpanEntity buildSpan(
            UUID taskId,
            String externalTraceId,
            String externalSpanId,
            UUID parentSpanId,
            String name,
            String kind,
            Instant startAt,
            long durationMs,
            String status,
            Map<String, Object> attributes,
            String input,
            String output,
            String sourceTool
    ) {
        SpanEntity entity = new SpanEntity();
        entity.setId(UUID.randomUUID());
        entity.setTaskId(taskId);
        entity.setExternalTraceId(externalTraceId);
        entity.setExternalSpanId(externalSpanId);
        entity.setParentSpanId(parentSpanId);
        entity.setName(name != null ? name : "span");
        entity.setKind(kind);
        entity.setStartAt(startAt != null ? startAt : Instant.now());
        entity.setDurationMs(durationMs);
        entity.setStatus(status);
        try {
            entity.setAttributes(attributes != null ? objectMapper.writeValueAsString(attributes) : null);
        } catch (Exception ignored) {
            entity.setAttributes(null);
        }
        entity.setInputRedacted(redactionService.redact(input));
        entity.setOutputRedacted(redactionService.redact(output));
        entity.setSourceTool(sourceTool);
        entity.setCreatedAt(Instant.now());
        return entity;
    }

    private TraceSpan toGraphql(SpanEntity span) {
        return new TraceSpan(
                span.getId(),
                span.getTaskId(),
                span.getExternalTraceId(),
                span.getName(),
                span.getKind(),
                span.getStartAt(),
                span.getDurationMs(),
                span.getStatus(),
                span.getParentSpanId(),
                span.getInputRedacted(),
                span.getOutputRedacted(),
                span.getSourceTool()
        );
    }
}

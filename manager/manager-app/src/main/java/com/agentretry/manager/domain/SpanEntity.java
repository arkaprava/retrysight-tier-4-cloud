/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "spans")
public class SpanEntity {
    @Id
    private UUID id;
    @Column(name = "task_id")
    private UUID taskId;
    @Column(name = "external_trace_id", length = 128)
    private String externalTraceId;
    @Column(name = "external_span_id", length = 128)
    private String externalSpanId;
    @Column(name = "parent_span_id")
    private UUID parentSpanId;
    @Column(nullable = false)
    private String name;
    @Column(length = 64)
    private String kind;
    @Column(name = "start_at", nullable = false)
    private Instant startAt;
    @Column(name = "duration_ms", nullable = false)
    private long durationMs;
    @Column(length = 16)
    private String status;
    private String attributes;
    @Column(name = "input_redacted")
    private String inputRedacted;
    @Column(name = "output_redacted")
    private String outputRedacted;
    @Column(name = "source_tool", length = 64)
    private String sourceTool;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTaskId() { return taskId; }
    public void setTaskId(UUID taskId) { this.taskId = taskId; }
    public String getExternalTraceId() { return externalTraceId; }
    public void setExternalTraceId(String externalTraceId) { this.externalTraceId = externalTraceId; }
    public String getExternalSpanId() { return externalSpanId; }
    public void setExternalSpanId(String externalSpanId) { this.externalSpanId = externalSpanId; }
    public UUID getParentSpanId() { return parentSpanId; }
    public void setParentSpanId(UUID parentSpanId) { this.parentSpanId = parentSpanId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public Instant getStartAt() { return startAt; }
    public void setStartAt(Instant startAt) { this.startAt = startAt; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAttributes() { return attributes; }
    public void setAttributes(String attributes) { this.attributes = attributes; }
    public String getInputRedacted() { return inputRedacted; }
    public void setInputRedacted(String inputRedacted) { this.inputRedacted = inputRedacted; }
    public String getOutputRedacted() { return outputRedacted; }
    public void setOutputRedacted(String outputRedacted) { this.outputRedacted = outputRedacted; }
    public String getSourceTool() { return sourceTool; }
    public void setSourceTool(String sourceTool) { this.sourceTool = sourceTool; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

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
@Table(name = "archive_runs")
public class ArchiveRunEntity {

    @Id
    private UUID id;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "dry_run", nullable = false)
    private boolean dryRun;

    @Column(length = 32)
    private String mode;

    @Column(name = "destination_type", length = 32)
    private String destinationType;

    @Column(name = "rows_exported", nullable = false)
    private long rowsExported;

    @Column(name = "rows_deleted", nullable = false)
    private long rowsDeleted;

    @Column(name = "bytes_written", nullable = false)
    private long bytesWritten;

    @Column(length = 255)
    private String datasets;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "manifest_summary")
    private String manifestSummary;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isDryRun() { return dryRun; }
    public void setDryRun(boolean dryRun) { this.dryRun = dryRun; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getDestinationType() { return destinationType; }
    public void setDestinationType(String destinationType) { this.destinationType = destinationType; }
    public long getRowsExported() { return rowsExported; }
    public void setRowsExported(long rowsExported) { this.rowsExported = rowsExported; }
    public long getRowsDeleted() { return rowsDeleted; }
    public void setRowsDeleted(long rowsDeleted) { this.rowsDeleted = rowsDeleted; }
    public long getBytesWritten() { return bytesWritten; }
    public void setBytesWritten(long bytesWritten) { this.bytesWritten = bytesWritten; }
    public String getDatasets() { return datasets; }
    public void setDatasets(String datasets) { this.datasets = datasets; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getManifestSummary() { return manifestSummary; }
    public void setManifestSummary(String manifestSummary) { this.manifestSummary = manifestSummary; }
}
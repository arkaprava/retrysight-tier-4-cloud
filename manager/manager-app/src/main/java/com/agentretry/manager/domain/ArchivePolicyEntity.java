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
@Table(name = "archive_policy")
public class ArchivePolicyEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false, length = 32)
    private String mode;

    @Column(name = "schedule_cron", nullable = false, length = 128)
    private String scheduleCron;

    @Column(name = "task_events_days", nullable = false)
    private int taskEventsDays;

    @Column(name = "token_usage_days", nullable = false)
    private int tokenUsageDays;

    @Column(name = "task_headers_days", nullable = false)
    private int taskHeadersDays;

    @Column(nullable = false, length = 255)
    private String datasets;

    @Column(name = "destination_type", nullable = false, length = 32)
    private String destinationType;

    @Column(name = "local_path", length = 1024)
    private String localPath;

    @Column(name = "network_path", length = 1024)
    private String networkPath;

    @Column(name = "s3_bucket", length = 255)
    private String s3Bucket;

    @Column(name = "s3_prefix", length = 512)
    private String s3Prefix;

    @Column(name = "s3_region", length = 64)
    private String s3Region;

    @Column(name = "s3_endpoint", length = 512)
    private String s3Endpoint;

    @Column(name = "s3_access_key_env", length = 255)
    private String s3AccessKeyEnv;

    @Column(name = "s3_secret_key_env", length = 255)
    private String s3SecretKeyEnv;

    @Column(name = "legal_hold", nullable = false)
    private boolean legalHold;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getScheduleCron() { return scheduleCron; }
    public void setScheduleCron(String scheduleCron) { this.scheduleCron = scheduleCron; }
    public int getTaskEventsDays() { return taskEventsDays; }
    public void setTaskEventsDays(int taskEventsDays) { this.taskEventsDays = taskEventsDays; }
    public int getTokenUsageDays() { return tokenUsageDays; }
    public void setTokenUsageDays(int tokenUsageDays) { this.tokenUsageDays = tokenUsageDays; }
    public int getTaskHeadersDays() { return taskHeadersDays; }
    public void setTaskHeadersDays(int taskHeadersDays) { this.taskHeadersDays = taskHeadersDays; }
    public String getDatasets() { return datasets; }
    public void setDatasets(String datasets) { this.datasets = datasets; }
    public String getDestinationType() { return destinationType; }
    public void setDestinationType(String destinationType) { this.destinationType = destinationType; }
    public String getLocalPath() { return localPath; }
    public void setLocalPath(String localPath) { this.localPath = localPath; }
    public String getNetworkPath() { return networkPath; }
    public void setNetworkPath(String networkPath) { this.networkPath = networkPath; }
    public String getS3Bucket() { return s3Bucket; }
    public void setS3Bucket(String s3Bucket) { this.s3Bucket = s3Bucket; }
    public String getS3Prefix() { return s3Prefix; }
    public void setS3Prefix(String s3Prefix) { this.s3Prefix = s3Prefix; }
    public String getS3Region() { return s3Region; }
    public void setS3Region(String s3Region) { this.s3Region = s3Region; }
    public String getS3Endpoint() { return s3Endpoint; }
    public void setS3Endpoint(String s3Endpoint) { this.s3Endpoint = s3Endpoint; }
    public String getS3AccessKeyEnv() { return s3AccessKeyEnv; }
    public void setS3AccessKeyEnv(String s3AccessKeyEnv) { this.s3AccessKeyEnv = s3AccessKeyEnv; }
    public String getS3SecretKeyEnv() { return s3SecretKeyEnv; }
    public void setS3SecretKeyEnv(String s3SecretKeyEnv) { this.s3SecretKeyEnv = s3SecretKeyEnv; }
    public boolean isLegalHold() { return legalHold; }
    public void setLegalHold(boolean legalHold) { this.legalHold = legalHold; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

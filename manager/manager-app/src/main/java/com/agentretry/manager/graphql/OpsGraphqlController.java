/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.graphql;

import com.agentretry.manager.security.SecadminGuard;
import com.agentretry.manager.service.AlertChannelService;
import com.agentretry.manager.service.ArchiveOrchestrator;
import com.agentretry.manager.service.ArchivePolicyService;
import com.agentretry.manager.service.ArchiveRestoreService;
import com.agentretry.manager.service.PartitionMaintenanceService;
import com.agentretry.manager.service.RetentionService;
import com.agentretry.manager.service.ScheduledReportService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.UUID;

@Controller
public class OpsGraphqlController {

    private final AlertChannelService alertChannelService;
    private final ScheduledReportService scheduledReportService;
    private final RetentionService retentionService;
    private final ArchivePolicyService archivePolicyService;
    private final ArchiveOrchestrator archiveOrchestrator;
    private final ArchiveRestoreService archiveRestoreService;
    private final PartitionMaintenanceService partitionMaintenanceService;

    public OpsGraphqlController(
            AlertChannelService alertChannelService,
            ScheduledReportService scheduledReportService,
            RetentionService retentionService,
            ArchivePolicyService archivePolicyService,
            ArchiveOrchestrator archiveOrchestrator,
            ArchiveRestoreService archiveRestoreService,
            PartitionMaintenanceService partitionMaintenanceService
    ) {
        this.alertChannelService = alertChannelService;
        this.scheduledReportService = scheduledReportService;
        this.retentionService = retentionService;
        this.archivePolicyService = archivePolicyService;
        this.archiveOrchestrator = archiveOrchestrator;
        this.archiveRestoreService = archiveRestoreService;
        this.partitionMaintenanceService = partitionMaintenanceService;
    }

    @QueryMapping
    public List<AlertChannel> alertChannels(Authentication authentication) {
        SecadminGuard.requireSecadmin(authentication);
        return alertChannelService.list();
    }

    @MutationMapping
    public AlertChannel createAlertChannel(
            Authentication authentication,
            @Argument String name,
            @Argument String channelType,
            @Argument String target
    ) {
        SecadminGuard.requireSecadmin(authentication);
        return alertChannelService.create(name, channelType, target);
    }

    @MutationMapping
    public boolean deleteAlertChannel(Authentication authentication, @Argument UUID id) {
        SecadminGuard.requireSecadmin(authentication);
        return alertChannelService.delete(id);
    }

    @QueryMapping
    public List<ScheduledReport> scheduledReports(Authentication authentication) {
        SecadminGuard.requireSecadmin(authentication);
        return scheduledReportService.list();
    }

    @MutationMapping
    public ScheduledReport createScheduledReport(
            Authentication authentication,
            @Argument String name,
            @Argument String dataset,
            @Argument String format,
            @Argument String cronExpression,
            @Argument String emailTo
    ) {
        SecadminGuard.requireSecadmin(authentication);
        return scheduledReportService.create(name, dataset, format, cronExpression, emailTo);
    }

    @MutationMapping
    public boolean deleteScheduledReport(Authentication authentication, @Argument UUID id) {
        SecadminGuard.requireSecadmin(authentication);
        return scheduledReportService.delete(id);
    }

    @QueryMapping
    public RetentionSettings retentionSettings(Authentication authentication) {
        SecadminGuard.requireSecadmin(authentication);
        return retentionService.getSettings();
    }

    @MutationMapping
    public RetentionSettings updateRetentionSettings(Authentication authentication, @Argument int taskEventsDays) {
        SecadminGuard.requireSecadmin(authentication);
        return retentionService.updateSettings(taskEventsDays);
    }

    @QueryMapping
    public ArchivePolicy archivePolicy(Authentication authentication) {
        SecadminGuard.requireSecadmin(authentication);
        return archivePolicyService.getPolicy();
    }

    @MutationMapping
    public ArchivePolicy updateArchivePolicy(Authentication authentication, @Argument ArchivePolicyInput input) {
        SecadminGuard.requireSecadmin(authentication);
        return archivePolicyService.updatePolicy(
                input.enabled(),
                input.mode(),
                input.scheduleCron(),
                input.taskEventsDays(),
                input.tokenUsageDays(),
                input.taskHeadersDays(),
                input.datasets(),
                input.destinationType(),
                input.localPath(),
                input.networkPath(),
                input.s3Bucket(),
                input.s3Prefix(),
                input.s3Region(),
                input.s3Endpoint(),
                input.s3AccessKeyEnv(),
                input.s3SecretKeyEnv(),
                input.legalHold()
        );
    }

    @MutationMapping
    public boolean testArchiveDestination(Authentication authentication) throws Exception {
        SecadminGuard.requireSecadmin(authentication);
        archivePolicyService.testDestination();
        return true;
    }

    @MutationMapping
    public ArchiveRun runArchiveJob(Authentication authentication, @Argument Boolean dryRun) {
        SecadminGuard.requireSecadmin(authentication);
        return archiveOrchestrator.runArchive(Boolean.TRUE.equals(dryRun));
    }

    @QueryMapping
    public List<ArchiveRun> archiveRuns(Authentication authentication) {
        SecadminGuard.requireSecadmin(authentication);
        return archiveOrchestrator.recentRuns();
    }

    @QueryMapping
    public List<ArchiveRestoreItem> archiveColdObjects(
            Authentication authentication,
            @Argument String dataset,
            @Argument String dayPrefix
    ) throws Exception {
        SecadminGuard.requireSecadmin(authentication);
        return archiveRestoreService.listCold(dataset, dayPrefix);
    }

    @MutationMapping
    public int rehydrateTokenUsageArchive(Authentication authentication, @Argument String path) throws Exception {
        SecadminGuard.requireSecadmin(authentication);
        return archiveRestoreService.rehydrateTokenUsage(path);
    }

    @QueryMapping
    public List<HotDataPartition> hotDataPartitions(Authentication authentication) {
        SecadminGuard.requireSecadmin(authentication);
        return partitionMaintenanceService.listPartitions().stream()
                .map(p -> new HotDataPartition(
                        p.id(), p.dataset(), p.periodStart(), p.periodEnd(), p.status(), p.notes(), p.createdAt()))
                .toList();
    }
}

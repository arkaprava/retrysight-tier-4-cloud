/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.service;

import com.agentretry.manager.archive.ArchiveDestinationType;
import com.agentretry.manager.archive.ArchiveMode;
import com.agentretry.manager.archive.ArchiveSink;
import com.agentretry.manager.archive.ArchiveSinkFactory;
import com.agentretry.manager.domain.ArchivePolicyEntity;
import com.agentretry.manager.graphql.ArchiveCapabilities;
import com.agentretry.manager.graphql.ArchivePolicy;
import com.agentretry.manager.platform.FeatureGateService;
import com.agentretry.manager.platform.PlatformFeature;
import com.agentretry.manager.repository.ArchivePolicyRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ArchivePolicyService {

    public static final UUID POLICY_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final ArchivePolicyRepository repository;
    private final ArchiveSinkFactory sinkFactory;
    private final FeatureGateService featureGateService;

    public ArchivePolicyService(
            ArchivePolicyRepository repository,
            ArchiveSinkFactory sinkFactory,
            FeatureGateService featureGateService
    ) {
        this.repository = repository;
        this.sinkFactory = sinkFactory;
        this.featureGateService = featureGateService;
    }

    public ArchiveCapabilities capabilities() {
        return new ArchiveCapabilities(
                featureGateService.isEnabled(PlatformFeature.RETENTION_PURGE),
                featureGateService.isEnabled(PlatformFeature.ARCHIVE_LOCAL),
                featureGateService.isEnabled(PlatformFeature.ARCHIVE_NETWORK),
                featureGateService.isEnabled(PlatformFeature.ARCHIVE_OBJECT_STORAGE),
                featureGateService.isEnabled(PlatformFeature.ARCHIVE_RESTORE_QUERY),
                featureGateService.isEnabled(PlatformFeature.ROLLUP_ANALYTICS),
                featureGateService.isEnabled(PlatformFeature.PARTITION_MAINTENANCE)
        );
    }

    @Transactional
    public ArchivePolicy getPolicy() {
        return toGraphql(requireEntity());
    }

    @Transactional
    public ArchivePolicy updatePolicy(
            Boolean enabled,
            String mode,
            String scheduleCron,
            Integer taskEventsDays,
            Integer tokenUsageDays,
            Integer taskHeadersDays,
            List<String> datasets,
            String destinationType,
            String localPath,
            String networkPath,
            String s3Bucket,
            String s3Prefix,
            String s3Region,
            String s3Endpoint,
            String s3AccessKeyEnv,
            String s3SecretKeyEnv,
            Boolean legalHold
    ) {
        ArchivePolicyEntity entity = requireEntity();
        if (enabled != null) {
            entity.setEnabled(enabled);
        }
        if (mode != null && !mode.isBlank()) {
            entity.setMode(ArchiveMode.valueOf(mode.trim().toUpperCase(Locale.ROOT)).name());
        }
        if (scheduleCron != null && !scheduleCron.isBlank()) {
            entity.setScheduleCron(scheduleCron.trim());
        }
        if (taskEventsDays != null) {
            entity.setTaskEventsDays(clampDays(taskEventsDays));
        }
        if (tokenUsageDays != null) {
            entity.setTokenUsageDays(clampDays(tokenUsageDays));
        }
        if (taskHeadersDays != null) {
            entity.setTaskHeadersDays(clampDays(taskHeadersDays));
        }
        if (datasets != null && !datasets.isEmpty()) {
            entity.setDatasets(String.join(",", datasets.stream().map(s -> s.trim().toUpperCase(Locale.ROOT)).toList()));
        }
        if (destinationType != null && !destinationType.isBlank()) {
            ArchiveDestinationType dest = ArchiveDestinationType.valueOf(destinationType.trim().toUpperCase(Locale.ROOT));
            ensureDestinationAllowed(dest);
            entity.setDestinationType(dest.name());
        }
        if (localPath != null) {
            entity.setLocalPath(localPath.isBlank() ? null : localPath.trim());
        }
        if (networkPath != null) {
            entity.setNetworkPath(networkPath.isBlank() ? null : networkPath.trim());
        }
        if (s3Bucket != null) {
            entity.setS3Bucket(s3Bucket.isBlank() ? null : s3Bucket.trim());
        }
        if (s3Prefix != null) {
            entity.setS3Prefix(s3Prefix.isBlank() ? null : s3Prefix.trim());
        }
        if (s3Region != null) {
            entity.setS3Region(s3Region.isBlank() ? null : s3Region.trim());
        }
        if (s3Endpoint != null) {
            entity.setS3Endpoint(s3Endpoint.isBlank() ? null : s3Endpoint.trim());
        }
        if (s3AccessKeyEnv != null) {
            entity.setS3AccessKeyEnv(s3AccessKeyEnv.isBlank() ? null : s3AccessKeyEnv.trim());
        }
        if (s3SecretKeyEnv != null) {
            entity.setS3SecretKeyEnv(s3SecretKeyEnv.isBlank() ? null : s3SecretKeyEnv.trim());
        }
        if (legalHold != null) {
            entity.setLegalHold(legalHold);
        }
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);
        return toGraphql(entity);
    }

    public void testDestination() throws Exception {
        ArchivePolicyEntity entity = requireEntity();
        ensureDestinationAllowed(ArchiveDestinationType.valueOf(entity.getDestinationType()));
        ArchiveSink sink = sinkFactory.create(entity);
        try {
            sink.testConnection();
        } finally {
            if (sink instanceof AutoCloseable closeable) {
                closeable.close();
            }
        }
    }

    @Transactional
    public ArchivePolicyEntity requireEntity() {
        return repository.findById(POLICY_ID).orElseGet(this::defaultEntity);
    }

    private ArchivePolicyEntity defaultEntity() {
        ArchivePolicyEntity entity = new ArchivePolicyEntity();
        entity.setId(POLICY_ID);
        entity.setEnabled(true);
        entity.setMode(ArchiveMode.DELETE_ONLY.name());
        entity.setScheduleCron("0 30 2 * * *");
        entity.setTaskEventsDays(90);
        entity.setTokenUsageDays(90);
        entity.setTaskHeadersDays(365);
        entity.setDatasets("TASK_EVENTS,TOKEN_USAGE");
        entity.setDestinationType(ArchiveDestinationType.LOCAL_FS.name());
        entity.setLocalPath("~/.agent-retry/archive");
        entity.setLegalHold(false);
        entity.setUpdatedAt(Instant.now());
        return repository.save(entity);
    }

    private void ensureDestinationAllowed(ArchiveDestinationType dest) {
        boolean allowed = switch (dest) {
            case LOCAL_FS -> featureGateService.isEnabled(PlatformFeature.ARCHIVE_LOCAL)
                    || featureGateService.isEnabled(PlatformFeature.RETENTION_PURGE);
            case NETWORK_FS -> featureGateService.isEnabled(PlatformFeature.ARCHIVE_NETWORK);
            case S3 -> featureGateService.isEnabled(PlatformFeature.ARCHIVE_OBJECT_STORAGE);
        };
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Destination " + dest + " is not available on the current installation tier");
        }
    }

    private ArchivePolicy toGraphql(ArchivePolicyEntity entity) {
        List<String> datasets = Arrays.stream(entity.getDatasets().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        return new ArchivePolicy(
                entity.isEnabled(),
                entity.getMode(),
                entity.getScheduleCron(),
                entity.getTaskEventsDays(),
                entity.getTokenUsageDays(),
                entity.getTaskHeadersDays(),
                datasets,
                entity.getDestinationType(),
                entity.getLocalPath(),
                entity.getNetworkPath(),
                entity.getS3Bucket(),
                entity.getS3Prefix(),
                entity.getS3Region(),
                entity.getS3Endpoint(),
                entity.getS3AccessKeyEnv(),
                entity.getS3SecretKeyEnv(),
                entity.isLegalHold(),
                entity.getUpdatedAt(),
                capabilities()
        );
    }

    private static int clampDays(int days) {
        if (days < 1 || days > 3650) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "days must be between 1 and 3650");
        }
        return days;
    }
}

/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.service;

import com.agentretry.manager.config.RetentionProperties;
import com.agentretry.manager.domain.RetentionSettingsEntity;
import com.agentretry.manager.graphql.RetentionSettings;
import com.agentretry.manager.repository.RetentionSettingsRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Compatibility wrapper around {@link ArchivePolicyService} for the legacy
 * task-events retention GraphQL API. Purge/archive execution lives in {@link ArchiveOrchestrator}.
 */
@Service
public class RetentionService {
    private static final UUID SINGLETON_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final RetentionProperties properties;
    private final RetentionSettingsRepository settingsRepository;
    private final ArchivePolicyService archivePolicyService;

    public RetentionService(
            RetentionProperties properties,
            RetentionSettingsRepository settingsRepository,
            ArchivePolicyService archivePolicyService
    ) {
        this.properties = properties;
        this.settingsRepository = settingsRepository;
        this.archivePolicyService = archivePolicyService;
    }

    @Transactional(readOnly = true)
    public RetentionSettings getSettings() {
        var policy = archivePolicyService.getPolicy();
        return new RetentionSettings(policy.taskEventsDays(), policy.updatedAt());
    }

    @Transactional
    public RetentionSettings updateSettings(int taskEventsDays) {
        if (taskEventsDays < 1 || taskEventsDays > 3650) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "taskEventsDays must be between 1 and 3650");
        }
        RetentionSettingsEntity entity = settingsRepository.findById(SINGLETON_ID).orElseGet(() -> {
            RetentionSettingsEntity created = new RetentionSettingsEntity();
            created.setId(SINGLETON_ID);
            return created;
        });
        entity.setTaskEventsDays(taskEventsDays);
        entity.setUpdatedAt(Instant.now());
        settingsRepository.save(entity);

        archivePolicyService.updatePolicy(
                null, null, null, taskEventsDays, null, null, null, null,
                null, null, null, null, null, null, null, null, null
        );
        return new RetentionSettings(taskEventsDays, entity.getUpdatedAt());
    }
}

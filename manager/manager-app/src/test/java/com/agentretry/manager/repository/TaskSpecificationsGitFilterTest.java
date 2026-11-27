/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.repository;

import com.agentretry.contract.SourceTool;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class TaskSpecificationsGitFilterTest {

    @Test
    void buildsSpecificationWithGitFilters() {
        Specification<com.agentretry.manager.domain.TaskEntity> spec = TaskSpecifications.filtered(
                UUID.randomUUID(),
                SourceTool.CURSOR,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-12-31T23:59:59Z"),
                1,
                "dev@example.com",
                "agent-retry-platform",
                "main",
                "github.com"
        );

        assertNotNull(spec);
    }
}

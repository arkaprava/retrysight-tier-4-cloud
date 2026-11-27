/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.repository;

import com.agentretry.contract.SourceTool;
import com.agentretry.manager.domain.TaskEntity;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class TaskSpecifications {

    private TaskSpecifications() {
    }

    public static Specification<TaskEntity> filtered(
            UUID agentId,
            SourceTool sourceTool,
            Instant from,
            Instant to,
            Integer minRetries,
            String user,
            String repo,
            String branch,
            String remoteHost
    ) {
        return (root, query, cb) -> {
            if (query != null
                    && !Long.class.equals(query.getResultType())
                    && !long.class.equals(query.getResultType())) {
                root.fetch("agent", JoinType.INNER);
                query.distinct(true);
            }

            List<Predicate> predicates = new ArrayList<>();

            if (agentId != null) {
                predicates.add(cb.equal(root.get("agent").get("id"), agentId));
            }
            if (sourceTool != null) {
                predicates.add(cb.equal(root.get("sourceTool"), sourceTool));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startedAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("startedAt"), to));
            }
            if (minRetries != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("retryCount"), minRetries));
            }
            if (user != null && !user.isBlank()) {
                String pattern = "%" + user.trim().toLowerCase() + "%";
                var agent = root.get("agent");
                predicates.add(cb.or(
                        cb.like(cb.lower(agent.get("osUsername")), pattern),
                        cb.like(cb.lower(agent.get("displayName")), pattern),
                        cb.like(cb.lower(agent.get("developerEmail")), pattern),
                        cb.like(cb.lower(agent.get("gitEmail")), pattern)
                ));
            }
            if (repo != null && !repo.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("repoName")), "%" + repo.trim().toLowerCase() + "%"));
            }
            if (branch != null && !branch.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("gitBranch")), branch.trim().toLowerCase()));
            }
            if (remoteHost != null && !remoteHost.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("remoteHost")), "%" + remoteHost.trim().toLowerCase() + "%"));
            }

            if (query != null) {
                query.orderBy(cb.desc(root.get("startedAt")));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}

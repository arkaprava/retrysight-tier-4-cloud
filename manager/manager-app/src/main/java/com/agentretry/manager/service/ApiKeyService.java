/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.service;

import com.agentretry.manager.config.ManagerAuthProperties;
import com.agentretry.manager.domain.ApiKeyEntity;
import com.agentretry.manager.graphql.ApiKeyCreated;
import com.agentretry.manager.graphql.ApiKeySummary;
import com.agentretry.manager.repository.ApiKeyRepository;
import com.agentretry.manager.security.ApiKeyHasher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class ApiKeyService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyHasher apiKeyHasher;
    private final ManagerAuthProperties authProperties;

    public ApiKeyService(
            ApiKeyRepository apiKeyRepository,
            ApiKeyHasher apiKeyHasher,
            ManagerAuthProperties authProperties
    ) {
        this.apiKeyRepository = apiKeyRepository;
        this.apiKeyHasher = apiKeyHasher;
        this.authProperties = authProperties;
    }

    @Transactional(readOnly = true)
    public List<ApiKeySummary> listKeys() {
        return apiKeyRepository.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(this::toSummary)
                .toList();
    }

    @Transactional
    public ApiKeyCreated createKey(String name, String role) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        String normalizedRole = normalizeRole(role);
        String rawKey = "apk_" + HexFormat.of().formatHex(randomBytes(24));
        String prefix = rawKey.substring(0, Math.min(12, rawKey.length()));

        ApiKeyEntity entity = new ApiKeyEntity();
        entity.setId(UUID.randomUUID());
        entity.setName(name.trim());
        entity.setKeyHash(apiKeyHasher.hash(rawKey));
        entity.setKeyPrefix(prefix);
        entity.setRole(normalizedRole);
        entity.setActive(true);
        entity.setCreatedAt(Instant.now());
        apiKeyRepository.save(entity);

        return new ApiKeyCreated(toSummary(entity), rawKey);
    }

    /**
     * Creates a replacement key and keeps the previous key valid until {@code grace_until}
     * so agents can roll credentials without a mass-401 window.
     */
    @Transactional
    public ApiKeyCreated rotateKey(UUID id) {
        ApiKeyEntity existing = apiKeyRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "API key not found"));
        Instant now = Instant.now();
        int graceSeconds = Math.max(0, authProperties.getApiKeyRotationGraceSeconds());
        existing.setActive(false);
        existing.setRevokedAt(now);
        if (graceSeconds > 0) {
            existing.setGraceUntil(now.plus(graceSeconds, ChronoUnit.SECONDS));
        } else {
            existing.setGraceUntil(null);
        }
        apiKeyRepository.save(existing);
        return createKey(existing.getName(), existing.getRole());
    }

    @Transactional
    public boolean revokeKey(UUID id) {
        ApiKeyEntity entity = apiKeyRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "API key not found"));
        entity.setActive(false);
        entity.setRevokedAt(Instant.now());
        entity.setGraceUntil(null);
        apiKeyRepository.save(entity);
        return true;
    }

    @Transactional
    public void touchLastUsed(String keyHash) {
        apiKeyRepository.findValidByKeyHash(keyHash, Instant.now()).ifPresent(entity -> {
            entity.setLastUsedAt(Instant.now());
            apiKeyRepository.save(entity);
        });
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return "AGENT";
        }
        String upper = role.trim().toUpperCase();
        if (!upper.equals("AGENT") && !upper.equals("UI")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "role must be AGENT or UI");
        }
        return upper;
    }

    private ApiKeySummary toSummary(ApiKeyEntity entity) {
        return new ApiKeySummary(
                entity.getId(),
                entity.getName(),
                entity.getKeyPrefix(),
                entity.getRole(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getLastUsedAt(),
                entity.getRevokedAt()
        );
    }

    private static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }
}

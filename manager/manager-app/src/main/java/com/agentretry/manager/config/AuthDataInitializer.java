/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.config;

import com.agentretry.manager.domain.ApiKeyEntity;
import com.agentretry.manager.repository.ApiKeyRepository;
import com.agentretry.manager.security.ApiKeyHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.UUID;

@Configuration
public class AuthDataInitializer {

    private static final Logger log = LoggerFactory.getLogger(AuthDataInitializer.class);
    private static final String DEFAULT_DEV_AGENT_KEY = "dev-agent-key";
    private static final String DEFAULT_DEV_UI_KEY = "dev-ui-key";

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CommandLineRunner seedAuthData(
            ApiKeyRepository apiKeyRepository,
            ApiKeyHasher apiKeyHasher,
            ManagerAuthProperties authProperties
    ) {
        return args -> {
            String configuredAgentKey = StringUtils.hasText(authProperties.getAgentApiKey())
                    ? authProperties.getAgentApiKey().trim()
                    : null;

            if (apiKeyRepository.count() == 0) {
                String agentKey = configuredAgentKey != null ? configuredAgentKey : DEFAULT_DEV_AGENT_KEY;
                seedApiKey(apiKeyRepository, apiKeyHasher, "Default Agent Key", agentKey, "AGENT");
                seedApiKey(apiKeyRepository, apiKeyHasher, "Default UI API Key", DEFAULT_DEV_UI_KEY, "UI");
                if (configuredAgentKey != null && !DEFAULT_DEV_AGENT_KEY.equals(configuredAgentKey)) {
                    log.info("Seeded AGENT API key from AGENT_API_KEY / manager.auth.agent-api-key");
                } else {
                    log.info("Seeded default dev AGENT API key ({})", DEFAULT_DEV_AGENT_KEY);
                }
                return;
            }

            // Non-empty DB: optionally ensure env-configured AGENT key exists (enterprise rollout).
            if (configuredAgentKey != null) {
                ensureAgentKey(apiKeyRepository, apiKeyHasher, configuredAgentKey);
            }
        };
    }

    private void ensureAgentKey(ApiKeyRepository repository, ApiKeyHasher hasher, String rawKey) {
        String hash = hasher.hash(rawKey);
        var existing = repository.findByKeyHash(hash);
        if (existing.isPresent()) {
            ApiKeyEntity entity = existing.get();
            if (!entity.isActive()) {
                entity.setActive(true);
                entity.setRevokedAt(null);
                entity.setGraceUntil(null);
                repository.save(entity);
                log.info("Re-activated AGENT API key from AGENT_API_KEY (prefix={})", entity.getKeyPrefix());
            }
            return;
        }
        seedApiKey(repository, hasher, "Env Agent Key", rawKey, "AGENT");
        log.info("Synced AGENT API key from AGENT_API_KEY / manager.auth.agent-api-key");
    }

    private void seedApiKey(
            ApiKeyRepository repository,
            ApiKeyHasher hasher,
            String name,
            String rawKey,
            String role
    ) {
        ApiKeyEntity entity = new ApiKeyEntity();
        entity.setId(UUID.randomUUID());
        entity.setName(name);
        entity.setKeyHash(hasher.hash(rawKey));
        entity.setKeyPrefix(rawKey.substring(0, Math.min(12, rawKey.length())));
        entity.setRole(role);
        entity.setActive(true);
        entity.setCreatedAt(Instant.now());
        repository.save(entity);
    }
}

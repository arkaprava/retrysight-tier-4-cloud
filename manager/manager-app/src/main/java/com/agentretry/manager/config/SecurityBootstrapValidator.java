/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Fails fast when non-dev profiles still use well-known default secrets.
 */
@Component
public class SecurityBootstrapValidator implements ApplicationRunner {

    private static final Set<String> HARDENED_PROFILES = Set.of("prod", "staging", "docker");

    private static final String DEFAULT_JWT = "agent-retry-platform-dev-secret-key-32chars";
    private static final String DEFAULT_SECADMIN_PASSWORD = "changeme";
    private static final String DEFAULT_SSO_KEY = "dev-sso-secret-encryption-key-32";
    private static final String DEFAULT_AGENT_KEY = "dev-agent-key";
    private static final String DEFAULT_POSTGRES_PASSWORD = "agent_retry";

    private final Environment environment;
    private final ManagerAuthProperties authProperties;

    public SecurityBootstrapValidator(Environment environment, ManagerAuthProperties authProperties) {
        this.environment = environment;
        this.authProperties = authProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!requiresHardening()) {
            return;
        }
        if (Boolean.parseBoolean(environment.getProperty("SECURITY_ALLOW_DEFAULT_SECRETS", "false"))) {
            return;
        }

        validateSecrets();
    }

    private void validateSecrets() {
        if (DEFAULT_JWT.equals(authProperties.getJwtSecret())) {
            throw new IllegalStateException(
                    "Refusing to start: JWT_SECRET / manager.auth.jwt-secret is still the dev default. "
                            + "Set JWT_SECRET before deploying with profile prod, staging, or docker.");
        }
        if (DEFAULT_SECADMIN_PASSWORD.equals(authProperties.getSecadmin().getPassword())) {
            throw new IllegalStateException(
                    "Refusing to start: SECADMIN_PASSWORD is still 'changeme'. "
                            + "Set a strong SECADMIN_PASSWORD before deploying.");
        }
        if (DEFAULT_SSO_KEY.equals(authProperties.getSso().getSecretEncryptionKey())) {
            throw new IllegalStateException(
                    "Refusing to start: SSO_SECRET_KEY is still the dev default. "
                            + "Set SSO_SECRET_KEY before deploying.");
        }

        String agentKey = resolveAgentApiKey();
        if (DEFAULT_AGENT_KEY.equals(agentKey)) {
            throw new IllegalStateException(
                    "Refusing to start: AGENT_API_KEY is still 'dev-agent-key'. "
                            + "Set AGENT_API_KEY before deploying.");
        }

        String dbPassword = environment.getProperty("spring.datasource.password");
        if (DEFAULT_POSTGRES_PASSWORD.equals(dbPassword)) {
            throw new IllegalStateException(
                    "Refusing to start: Postgres password is still the dev default 'agent_retry'. "
                            + "Set SPRING_DATASOURCE_PASSWORD / POSTGRES_PASSWORD before deploying.");
        }
    }

    private boolean requiresHardening() {
        String[] active = environment.getActiveProfiles();
        if (active.length == 0) {
            return false;
        }
        Set<String> profiles = Arrays.stream(active).collect(Collectors.toSet());
        return profiles.stream().anyMatch(HARDENED_PROFILES::contains);
    }

    private String resolveAgentApiKey() {
        if (StringUtils.hasText(authProperties.getAgentApiKey())) {
            return authProperties.getAgentApiKey().trim();
        }
        String env = environment.getProperty("AGENT_API_KEY");
        return env == null ? "" : env.trim();
    }
}

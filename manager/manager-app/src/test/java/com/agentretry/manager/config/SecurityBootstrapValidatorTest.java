/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecurityBootstrapValidatorTest {

    @Test
    void allowsDefaultsWithoutHardenedProfile() {
        ManagerAuthProperties props = defaultProperties();
        MockEnvironment env = new MockEnvironment();
        SecurityBootstrapValidator validator = new SecurityBootstrapValidator(env, props);
        assertDoesNotThrow(() -> validator.run(null));
    }

    @Test
    void rejectsDefaultJwtInProdProfile() {
        ManagerAuthProperties props = defaultProperties();
        MockEnvironment env = new MockEnvironment().withProperty("spring.profiles.active", "prod");
        SecurityBootstrapValidator validator = new SecurityBootstrapValidator(env, props);
        assertThrows(IllegalStateException.class, () -> validator.run(null));
    }

    @Test
    void acceptsCustomSecretsInDockerProfile() {
        ManagerAuthProperties props = new ManagerAuthProperties();
        props.setJwtSecret("custom-jwt-secret-32chars-minimum-length");
        props.getSecadmin().setPassword("StrongP@ssw0rd!");
        props.getSso().setSecretEncryptionKey("custom-sso-encryption-key-32b");
        props.setAgentApiKey("fleet-agent-key-prod");

        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.profiles.active", "docker")
                .withProperty("spring.datasource.password", "db-secret-password");
        SecurityBootstrapValidator validator = new SecurityBootstrapValidator(env, props);
        assertDoesNotThrow(() -> validator.run(null));
    }

    private static ManagerAuthProperties defaultProperties() {
        ManagerAuthProperties props = new ManagerAuthProperties();
        props.setJwtSecret("agent-retry-platform-dev-secret-key-32chars");
        props.getSecadmin().setPassword("changeme");
        props.getSso().setSecretEncryptionKey("dev-sso-secret-encryption-key-32");
        props.setAgentApiKey("dev-agent-key");
        return props;
    }
}

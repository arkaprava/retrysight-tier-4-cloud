/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.security;

import com.agentretry.manager.config.ManagerAuthProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SecretEncryptionServiceTest {

    @Test
    void encryptsAndDecryptsClientSecret() {
        ManagerAuthProperties properties = new ManagerAuthProperties();
        SecretEncryptionService service = new SecretEncryptionService(properties);

        String plaintext = "oidc-client-secret-value";
        String encrypted = service.encrypt(plaintext);

        assertNotEquals(plaintext, encrypted);
        assertEquals(plaintext, service.decrypt(encrypted));
    }
}

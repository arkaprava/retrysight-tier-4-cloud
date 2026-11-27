/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.security;

import com.agentretry.manager.config.ManagerAuthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiKeyHasherTest {

    private ApiKeyHasher hasher;

    @BeforeEach
    void setUp() {
        ManagerAuthProperties props = new ManagerAuthProperties();
        props.setJwtSecret("test-pepper-key-at-least-32-chars-long");
        hasher = new ApiKeyHasher(props);
    }

    @Test
    void hashUsesHmacPrefix() {
        String hashed = hasher.hash("my-secret-key");
        assertTrue(hashed.startsWith("h1:"));
        assertTrue(hashed.length() > 20);
    }

    @Test
    void matchesPreferredHash() {
        String raw = "apk_test_key_value";
        assertTrue(hasher.matches(raw, hasher.hash(raw)));
    }

    @Test
    void matchesLegacyTruncatedHash() {
        String raw = "dev-agent-key";
        String legacy = hasher.hashLegacy(raw);
        assertTrue(hasher.matches(raw, legacy));
    }

    @Test
    void rejectsWrongKey() {
        assertFalse(hasher.matches("wrong-key", hasher.hash("correct-key")));
    }

    @Test
    void differentKeysProduceDifferentHashes() {
        assertNotEquals(hasher.hash("key-a"), hasher.hash("key-b"));
    }
}

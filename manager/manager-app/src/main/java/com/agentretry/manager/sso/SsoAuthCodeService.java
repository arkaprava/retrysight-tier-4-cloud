/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.sso;

import com.agentretry.manager.graphql.AuthPayload;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single-use, short-lived SSO authorization codes so JWTs are not passed in redirect URLs.
 */
@Component
public class SsoAuthCodeService {

    private static final long TTL_MS = 60_000L;

    private final Map<String, StoredCode> codes = new ConcurrentHashMap<>();

    public String issue(AuthPayload payload) {
        purgeExpired();
        String code = UUID.randomUUID().toString();
        codes.put(code, new StoredCode(payload, System.currentTimeMillis() + TTL_MS));
        return code;
    }

    public AuthPayload exchange(String code) {
        purgeExpired();
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Invalid or expired SSO code");
        }
        StoredCode stored = codes.remove(code.trim());
        if (stored == null || stored.expiresAtMs < System.currentTimeMillis()) {
            throw new IllegalArgumentException("Invalid or expired SSO code");
        }
        return stored.payload;
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        codes.entrySet().removeIf(entry -> entry.getValue().expiresAtMs < now);
    }

    private record StoredCode(AuthPayload payload, long expiresAtMs) {
    }
}

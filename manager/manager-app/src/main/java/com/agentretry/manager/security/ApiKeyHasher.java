/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.security;

import com.agentretry.manager.config.ManagerAuthProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * HMAC-SHA256 API key hashing with a server pepper ({@code manager.auth.jwt-secret}).
 * Legacy rows store a truncated SHA-256 prefix (16 hex chars); {@link #matches} accepts both.
 */
@Component
public class ApiKeyHasher {

    private static final String HMAC_PREFIX = "h1:";
    private static final int LEGACY_HEX_LENGTH = 16;

    private final byte[] pepper;

    public ApiKeyHasher(ManagerAuthProperties authProperties) {
        String secret = authProperties.getJwtSecret();
        this.pepper = (secret == null ? "" : secret).getBytes(StandardCharsets.UTF_8);
    }

    /** Preferred storage format for new and rotated keys. */
    public String hash(String apiKey) {
        return HMAC_PREFIX + hmacHex(apiKey);
    }

    /** Legacy truncated SHA-256 (64-bit prefix) — lookup only for existing rows. */
    public String hashLegacy(String apiKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(apiKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed).substring(0, LEGACY_HEX_LENGTH);
        } catch (NoSuchAlgorithmException ex) {
            return Integer.toHexString(apiKey.hashCode());
        }
    }

    public boolean matches(String apiKey, String storedHash) {
        if (apiKey == null || storedHash == null || storedHash.isBlank()) {
            return false;
        }
        if (storedHash.startsWith(HMAC_PREFIX)) {
            return constantTimeEquals(storedHash, hash(apiKey));
        }
        if (storedHash.length() == LEGACY_HEX_LENGTH) {
            return constantTimeEquals(storedHash, hashLegacy(apiKey));
        }
        return constantTimeEquals(storedHash, hash(apiKey));
    }

    private String hmacHex(String apiKey) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pepper, "HmacSHA256"));
            byte[] out = mac.doFinal(apiKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", ex);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}

/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Component
public class JwtService {

    private final SecretKey secretKey;
    private final long expirationHours;

    public JwtService(
            @Value("${manager.auth.jwt-secret:agent-retry-platform-dev-secret-key-32chars}") String secret,
            @Value("${manager.auth.jwt-expiration-hours:24}") long expirationHours
    ) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, Math.min(keyBytes.length, 32));
            keyBytes = padded;
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationHours = expirationHours;
    }

    public String generateToken(UUID userId, String email) {
        return generateToken(userId, email, List.of("UI"));
    }

    public String generateToken(UUID userId, String email, List<String> roles) {
        return generateToken(userId, email, roles, expirationHours * 3600);
    }

    public String generateMcpToken(UUID userId, String email, List<String> roles, long expirationSeconds) {
        return generateToken(userId, email, roles, expirationSeconds);
    }

    public Instant tokenExpiration(long expirationSeconds) {
        return Instant.now().plusSeconds(expirationSeconds);
    }

    private String generateToken(UUID userId, String email, List<String> roles, long expirationSeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(secretKey)
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}

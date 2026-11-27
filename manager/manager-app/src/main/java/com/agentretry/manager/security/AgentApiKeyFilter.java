/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.security;

import com.agentretry.manager.domain.ApiKeyEntity;
import com.agentretry.manager.repository.ApiKeyRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class AgentApiKeyFilter extends OncePerRequestFilter {

    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyHasher apiKeyHasher;

    public AgentApiKeyFilter(ApiKeyRepository apiKeyRepository, ApiKeyHasher apiKeyHasher) {
        this.apiKeyRepository = apiKeyRepository;
        this.apiKeyHasher = apiKeyHasher;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/ingest") && !path.startsWith("/api/v1/otel");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String apiKey = resolveAgentApiKey(request);
        if (apiKey == null || apiKey.isBlank()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing X-Agent-Api-Key");
            return;
        }

        Optional<ApiKeyEntity> keyOpt = resolveValidAgentKey(apiKey);
        if (keyOpt.isEmpty()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid agent API key");
            return;
        }

        try {
            var entity = keyOpt.get();
            entity.setLastUsedAt(Instant.now());
            apiKeyRepository.save(entity);
        } catch (Exception ignored) {
            // Non-fatal: auth succeeds even if last-used stamp fails
        }

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "agent",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_AGENT"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
        filterChain.doFilter(request, response);
    }

    private Optional<ApiKeyEntity> resolveValidAgentKey(String apiKey) {
        Instant now = Instant.now();
        String preferredHash = apiKeyHasher.hash(apiKey);
        Optional<ApiKeyEntity> found = apiKeyRepository.findValidByKeyHash(preferredHash, now)
                .filter(key -> "AGENT".equals(key.getRole()));
        if (found.isPresent()) {
            return found;
        }

        String legacyHash = apiKeyHasher.hashLegacy(apiKey);
        found = apiKeyRepository.findValidByKeyHash(legacyHash, now)
                .filter(key -> "AGENT".equals(key.getRole()));
        if (found.isPresent()) {
            ApiKeyEntity entity = found.get();
            entity.setKeyHash(preferredHash);
            apiKeyRepository.save(entity);
            return Optional.of(entity);
        }
        return Optional.empty();
    }

    /**
     * Prefer {@code X-Agent-Api-Key}; for OTel ingest also accept {@code Authorization: Bearer &lt;agent-key&gt;}
     * (Claude Cowork OTLP header configuration).
     */
    private static String resolveAgentApiKey(HttpServletRequest request) {
        String headerKey = request.getHeader("X-Agent-Api-Key");
        if (headerKey != null && !headerKey.isBlank()) {
            return headerKey.trim();
        }
        String path = request.getRequestURI();
        if (path != null && path.startsWith("/api/v1/otel") && hasBearerToken(request)) {
            return request.getHeader(HttpHeaders.AUTHORIZATION).substring(7).trim();
        }
        return null;
    }

    private static boolean hasBearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        return header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)
                && header.length() > 7;
    }
}

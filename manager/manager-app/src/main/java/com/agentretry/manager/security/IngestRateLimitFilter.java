/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class IngestRateLimitFilter extends OncePerRequestFilter {

    private final SecurityRateLimiter rateLimiter;

    public IngestRateLimitFilter(SecurityRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
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
        String apiKey = resolveApiKey(request);
        rateLimiter.checkIngest(apiKey);
        rateLimiter.recordIngest(apiKey);
        filterChain.doFilter(request, response);
    }

    private static String resolveApiKey(HttpServletRequest request) {
        String headerKey = request.getHeader("X-Agent-Api-Key");
        if (headerKey != null && !headerKey.isBlank()) {
            return headerKey.trim();
        }
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)
                && authorization.length() > 7) {
            return authorization.substring(7).trim();
        }
        return request.getRemoteAddr();
    }
}

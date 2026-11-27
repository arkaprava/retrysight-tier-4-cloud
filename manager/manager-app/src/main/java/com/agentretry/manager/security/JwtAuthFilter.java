/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/v1/ingest")
                || path.startsWith("/api/v1/auth")
                || path.startsWith("/oauth2")
                || path.startsWith("/login/oauth2")
                || path.startsWith("/saml2")
                || path.startsWith("/login/saml2")
                || path.startsWith("/graphiql")
                || path.startsWith("/vendor/graphiql");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();
        boolean protectedPath = path.startsWith("/graphql")
                || path.startsWith("/api/v1/reports")
                || path.startsWith("/api/v1/chat")
                || path.startsWith("/api/v1/admin")
                || path.startsWith("/api/v1/archive")
                || path.startsWith("/api/v1/tasks/");

        if (!protectedPath) {
            filterChain.doFilter(request, response);
            return;
        }

        if (path.startsWith("/graphql") && "GET".equalsIgnoreCase(request.getMethod())) {
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (header != null && header.startsWith("Bearer ")) {
                try {
                    String token = header.substring(7);
                    Claims claims = jwtService.parseClaims(token);
                    String principal = claims.get("email", String.class);
                    if (principal == null || principal.isBlank()) {
                        principal = claims.getSubject();
                    }
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            JwtAuthorityExtractor.fromClaims(claims)
                    );
                    SecurityContextHolder.getContext().setAuthentication(auth);
                } catch (Exception ignored) {
                    SecurityContextHolder.clearContext();
                }
            }
            filterChain.doFilter(request, response);
            return;
        }

        if (path.startsWith("/api/v1/tasks/") && SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing Bearer token");
            return;
        }

        try {
            String token = header.substring(7);
            Claims claims = jwtService.parseClaims(token);
            String principal = claims.get("email", String.class);
            if (principal == null || principal.isBlank()) {
                principal = claims.getSubject();
            }
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    JwtAuthorityExtractor.fromClaims(claims)
            );
            SecurityContextHolder.getContext().setAuthentication(auth);
            filterChain.doFilter(request, response);
        } catch (Exception ex) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token");
        }
    }
}

/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.security;

import io.jsonwebtoken.Claims;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.graphql.server.WebSocketGraphQlInterceptor;
import org.springframework.graphql.server.WebSocketGraphQlRequest;
import org.springframework.graphql.server.WebSocketSessionInfo;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.Map;

@Component
public class AuthWebSocketInterceptor implements WebSocketGraphQlInterceptor {

    private static final String SECURITY_CONTEXT_ATTR =
            AuthWebSocketInterceptor.class.getName() + ".SECURITY_CONTEXT";

    private final JwtService jwtService;

    public AuthWebSocketInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Mono<Object> handleConnectionInitialization(
            WebSocketSessionInfo sessionInfo,
            Map<String, Object> connectionInitPayload
    ) {
        String bearerToken = extractBearerToken(connectionInitPayload);
        if (bearerToken == null) {
            return Mono.error(new IllegalArgumentException("Missing Bearer token"));
        }

        try {
            Claims claims = jwtService.parseClaims(bearerToken);
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    claims.getSubject(),
                    null,
                    JwtAuthorityExtractor.fromClaims(claims)
            );
            SecurityContext securityContext = new SecurityContextImpl(auth);
            sessionInfo.getAttributes().put(SECURITY_CONTEXT_ATTR, securityContext);
            return Mono.empty();
        } catch (Exception ex) {
            return Mono.error(new IllegalArgumentException("Invalid token", ex));
        }
    }

    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, WebGraphQlInterceptor.Chain chain) {
        if (!(request instanceof WebSocketGraphQlRequest wsRequest)) {
            return chain.next(request);
        }

        SecurityContext securityContext = (SecurityContext) wsRequest.getSessionInfo()
                .getAttributes()
                .get(SECURITY_CONTEXT_ATTR);
        if (securityContext == null) {
            return Mono.error(new IllegalStateException("Unauthorized WebSocket subscription"));
        }

        Context reactorContext = Context.of(SecurityContext.class.getName(), securityContext);
        return chain.next(request)
                .contextWrite(reactorContext)
                .doFirst(() -> SecurityContextHolder.setContext(securityContext))
                .doFinally(st -> SecurityContextHolder.clearContext());
    }

    private static String extractBearerToken(Map<String, Object> connectionInitPayload) {
        Object authHeader = connectionInitPayload.get("Authorization");
        if (authHeader == null) {
            authHeader = connectionInitPayload.get("authorization");
        }
        if (!(authHeader instanceof String header) || !header.startsWith("Bearer ")) {
            return null;
        }
        return header.substring(7);
    }
}

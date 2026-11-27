/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.auth;

import com.agentretry.manager.graphql.AuthPayload;
import com.agentretry.manager.graphql.McpTokenPayload;
import com.agentretry.manager.platform.FeatureGateService;
import com.agentretry.manager.security.SecurityRateLimiter;
import com.agentretry.manager.service.AuthService;
import com.agentretry.manager.sso.SsoAuthCodeService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final FeatureGateService featureGateService;
    private final SecurityRateLimiter rateLimiter;
    private final SsoAuthCodeService ssoAuthCodeService;

    public AuthController(
            AuthService authService,
            FeatureGateService featureGateService,
            SecurityRateLimiter rateLimiter,
            SsoAuthCodeService ssoAuthCodeService
    ) {
        this.authService = authService;
        this.featureGateService = featureGateService;
        this.rateLimiter = rateLimiter;
        this.ssoAuthCodeService = ssoAuthCodeService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthPayload> login(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String clientKey = clientKey(request);
        rateLimiter.checkLoginAllowed(clientKey);
        String email = body.getOrDefault("email", "");
        String password = body.getOrDefault("password", "");
        try {
            return ResponseEntity.ok(authService.login(email, password));
        } catch (IllegalArgumentException ex) {
            rateLimiter.recordLoginFailure(clientKey);
            throw ex;
        }
    }

    @PostMapping("/sso/exchange")
    public ResponseEntity<AuthPayload> ssoExchange(@RequestBody Map<String, String> body) {
        String code = body.getOrDefault("code", "");
        return ResponseEntity.ok(ssoAuthCodeService.exchange(code));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> loginFailed(IllegalArgumentException ex) {
        String message = ex.getMessage() != null ? ex.getMessage() : "Invalid credentials";
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", message));
    }

    @PostMapping("/mcp-token")
    public ResponseEntity<McpTokenPayload> mcpToken(
            @RequestBody Map<String, String> body,
            HttpServletRequest request
    ) {
        if (!featureGateService.isMcpEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "MCP integration is not enabled for this installation");
        }
        String clientKey = clientKey(request);
        rateLimiter.checkMcpToken(clientKey);
        rateLimiter.recordMcpTokenRequest(clientKey);
        String email = body.getOrDefault("email", "");
        String password = body.getOrDefault("password", "");
        try {
            return ResponseEntity.ok(authService.issueMcpToken(email, password));
        } catch (IllegalArgumentException ex) {
            rateLimiter.recordLoginFailure(clientKey);
            throw ex;
        }
    }

    private static String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

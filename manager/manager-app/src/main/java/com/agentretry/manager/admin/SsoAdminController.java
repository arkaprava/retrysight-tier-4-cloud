/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.admin;

import com.agentretry.manager.sso.SsoConfigService;
import com.agentretry.manager.sso.SsoTestService;
import com.agentretry.manager.sso.dto.SsoConfigRequest;
import com.agentretry.manager.sso.dto.SsoConfigResponse;
import com.agentretry.manager.sso.dto.SsoTestResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/admin/sso")
public class SsoAdminController {

    private final SsoConfigService ssoConfigService;
    private final SsoTestService ssoTestService;

    public SsoAdminController(SsoConfigService ssoConfigService, SsoTestService ssoTestService) {
        this.ssoConfigService = ssoConfigService;
        this.ssoTestService = ssoTestService;
    }

    @GetMapping("/config")
    public SsoConfigResponse getConfig(Authentication authentication) {
        requireSecadmin(authentication);
        return ssoConfigService.getConfig();
    }

    @PutMapping("/config")
    public SsoConfigResponse saveConfig(
            Authentication authentication,
            @RequestBody SsoConfigRequest request
    ) {
        String email = requireSecadmin(authentication);
        return ssoConfigService.saveConfig(request, email);
    }

    @PostMapping("/test")
    public SsoTestResponse testConnection(Authentication authentication) {
        requireSecadmin(authentication);
        return ssoTestService.testConnection();
    }

    @PostMapping("/enable")
    public SsoConfigResponse enableSso(Authentication authentication) {
        requireSecadmin(authentication);
        return ssoConfigService.enableSso();
    }

    @DeleteMapping("/config")
    public ResponseEntity<Void> deleteConfig(Authentication authentication) {
        requireSecadmin(authentication);
        ssoConfigService.deleteConfig();
        return ResponseEntity.noContent().build();
    }

    private static String requireSecadmin(Authentication authentication) {
        if (authentication == null || !hasSecadminRole(authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "SECADMIN role required");
        }
        return authentication.getName();
    }

    private static boolean hasSecadminRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_SECADMIN"::equals);
    }
}

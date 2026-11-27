/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.auth;

import com.agentretry.manager.sso.SsoStatusService;
import com.agentretry.manager.sso.dto.SsoStatusResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class SsoStatusController {

    private final SsoStatusService ssoStatusService;

    public SsoStatusController(SsoStatusService ssoStatusService) {
        this.ssoStatusService = ssoStatusService;
    }

    @GetMapping("/sso/status")
    public SsoStatusResponse status() {
        return ssoStatusService.getStatus();
    }
}

/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.graphql;

import com.agentretry.manager.service.AuthService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;

@Controller
public class AuthGraphqlController {

    private final AuthService authService;

    public AuthGraphqlController(AuthService authService) {
        this.authService = authService;
    }

    @MutationMapping
    public AuthPayload login(@Argument String email, @Argument String password) {
        return authService.login(email, password);
    }
}

/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.sso;

import com.agentretry.manager.config.ManagerAuthProperties;
import com.agentretry.manager.domain.AuthProvider;
import com.agentretry.manager.domain.IdentityProviderConfigEntity;
import com.agentretry.manager.domain.IdentityProviderType;
import com.agentretry.manager.domain.UiUserEntity;
import com.agentretry.manager.domain.UiUserRole;
import com.agentretry.manager.repository.IdentityProviderConfigRepository;
import com.agentretry.manager.repository.UiUserRepository;
import com.agentretry.manager.security.JwtService;
import com.agentretry.manager.security.LocalLoginPolicy;
import com.agentretry.manager.security.SsoStateService;
import com.agentretry.manager.service.AuthService;
import com.agentretry.manager.sso.SsoAuthCodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SsoAuthenticationSuccessHandlerTest {

    private UiUserRepository uiUserRepository;
    private AuthService authService;
    private SsoAuthenticationSuccessHandler handler;
    private IdentityProviderConfigRepository identityProviderConfigRepository;

    @BeforeEach
    void setUp() {
        identityProviderConfigRepository = mock(IdentityProviderConfigRepository.class);
        SsoStateService ssoStateService = new SsoStateService(identityProviderConfigRepository);
        uiUserRepository = mock(UiUserRepository.class);
        authService = new AuthService(
                uiUserRepository,
                new BCryptPasswordEncoder(),
                new JwtService("agent-retry-platform-dev-secret-key-32chars", 24),
                new LocalLoginPolicy(new ManagerAuthProperties(), ssoStateService, new BCryptPasswordEncoder())
        );
        ManagerAuthProperties properties = new ManagerAuthProperties();
        properties.getSso().setCallbackBaseUrl("http://localhost:5173");
        handler = new SsoAuthenticationSuccessHandler(
                ssoStateService,
                uiUserRepository,
                authService,
                properties,
                new SsoAuthCodeService()
        );
    }

    @Test
    void mapsSuperuserGroupToSecadminRole() throws Exception {
        IdentityProviderConfigEntity config = new IdentityProviderConfigEntity();
        config.setId(UUID.randomUUID());
        config.setProviderType(IdentityProviderType.OIDC);
        config.setEnabled(true);
        config.setEmailAttribute("email");
        config.setDisplayNameAttribute("name");
        config.setGroupsAttribute("groups");
        config.setSuperuserGroup("platform-admins");
        when(identityProviderConfigRepository.findFirstByEnabledTrue()).thenReturn(Optional.of(config));

        when(uiUserRepository.findByExternalSubject("subject-1")).thenReturn(Optional.empty());
        when(uiUserRepository.save(any(UiUserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OidcIdToken idToken = new OidcIdToken(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of(
                        "sub", "subject-1",
                        "email", "admin@company.com",
                        "name", "Platform Admin",
                        "groups", List.of("platform-admins")
                )
        );
        OidcUser oidcUser = new DefaultOidcUser(List.of(), idToken, "sub");
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(oidcUser, null, oidcUser.getAuthorities());

        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

        assertTrue(response.getRedirectedUrl().contains("/auth/callback"));
        assertTrue(response.getRedirectedUrl().contains("code="));
        assertFalse(response.getRedirectedUrl().contains("token="));

        var captor = org.mockito.ArgumentCaptor.forClass(UiUserEntity.class);
        verify(uiUserRepository).save(captor.capture());
        UiUserEntity saved = captor.getValue();
        assertEquals(UiUserRole.SECADMIN, saved.getRole());
        assertEquals(AuthProvider.OIDC, saved.getAuthProvider());
    }
}

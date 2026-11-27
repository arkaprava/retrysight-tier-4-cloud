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
import com.agentretry.manager.graphql.AuthPayload;
import com.agentretry.manager.repository.UiUserRepository;
import com.agentretry.manager.security.SsoStateService;
import com.agentretry.manager.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class SsoAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final SsoStateService ssoStateService;
    private final UiUserRepository uiUserRepository;
    private final AuthService authService;
    private final ManagerAuthProperties authProperties;
    private final SsoAuthCodeService ssoAuthCodeService;

    public SsoAuthenticationSuccessHandler(
            SsoStateService ssoStateService,
            UiUserRepository uiUserRepository,
            AuthService authService,
            ManagerAuthProperties authProperties,
            SsoAuthCodeService ssoAuthCodeService
    ) {
        this.ssoStateService = ssoStateService;
        this.uiUserRepository = uiUserRepository;
        this.authService = authService;
        this.authProperties = authProperties;
        this.ssoAuthCodeService = ssoAuthCodeService;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {
        IdentityProviderConfigEntity config = ssoStateService.getActiveConfig()
                .orElseThrow(() -> new IllegalStateException("SSO is not enabled"));

        SsoUserAttributes attributes = extractAttributes(authentication, config);
        UiUserEntity user = provisionUser(attributes, config);
        AuthPayload payload = authService.issueSsoToken(user);
        String code = ssoAuthCodeService.issue(payload);

        String redirectUrl = UriComponentsBuilder
                .fromUriString(authProperties.getSso().getCallbackBaseUrl() + "/auth/callback")
                .queryParam("code", code)
                .build()
                .encode()
                .toUriString();
        response.sendRedirect(redirectUrl);
    }

    private UiUserEntity provisionUser(SsoUserAttributes attributes, IdentityProviderConfigEntity config) {
        Optional<UiUserEntity> existing = uiUserRepository.findByExternalSubject(attributes.subject());
        UiUserEntity user = existing.orElseGet(UiUserEntity::new);
        if (user.getId() == null) {
            user.setId(UUID.randomUUID());
            user.setCreatedAt(Instant.now());
        }
        user.setEmail(attributes.email());
        user.setDisplayName(attributes.displayName());
        user.setExternalSubject(attributes.subject());
        user.setPasswordHash(null);
        user.setActive(true);
        user.setAuthProvider(config.getProviderType() == IdentityProviderType.OIDC ? AuthProvider.OIDC : AuthProvider.SAML);
        user.setRole(resolveRole(attributes.groups(), config.getSuperuserGroup()));
        return uiUserRepository.save(user);
    }

    private UiUserRole resolveRole(Collection<String> groups, String superuserGroup) {
        if (superuserGroup != null && groups.stream().anyMatch(group -> group.equalsIgnoreCase(superuserGroup))) {
            return UiUserRole.SECADMIN;
        }
        return UiUserRole.UI;
    }

    private SsoUserAttributes extractAttributes(Authentication authentication, IdentityProviderConfigEntity config) {
        if (authentication.getPrincipal() instanceof OidcUser oidcUser) {
            return fromOidc(oidcUser, config);
        }
        if (authentication.getPrincipal() instanceof OAuth2User oauth2User) {
            return fromOAuth2(oauth2User, config);
        }
        if (authentication.getPrincipal() instanceof Saml2AuthenticatedPrincipal samlPrincipal) {
            return fromSaml(samlPrincipal, config);
        }
        throw new IllegalStateException("Unsupported SSO principal type");
    }

    private SsoUserAttributes fromOidc(OidcUser user, IdentityProviderConfigEntity config) {
        String email = firstNonBlank(
                attribute(user.getAttributes(), config.getEmailAttribute()),
                user.getEmail(),
                user.getPreferredUsername()
        );
        String displayName = firstNonBlank(
                attribute(user.getAttributes(), config.getDisplayNameAttribute()),
                user.getFullName(),
                email
        );
        Collection<String> groups = groups(user.getAttributes(), config.getGroupsAttribute());
        return new SsoUserAttributes(user.getSubject(), email, displayName, groups);
    }

    private SsoUserAttributes fromOAuth2(OAuth2User user, IdentityProviderConfigEntity config) {
        Map<String, Object> attributes = user.getAttributes();
        String email = firstNonBlank(attribute(attributes, config.getEmailAttribute()), user.getName());
        String displayName = firstNonBlank(attribute(attributes, config.getDisplayNameAttribute()), email);
        return new SsoUserAttributes(user.getName(), email, displayName, groups(attributes, config.getGroupsAttribute()));
    }

    private SsoUserAttributes fromSaml(Saml2AuthenticatedPrincipal principal, IdentityProviderConfigEntity config) {
        String email = firstNonBlank(samlAttribute(principal, config.getEmailAttribute()), principal.getName());
        String displayName = firstNonBlank(samlAttribute(principal, config.getDisplayNameAttribute()), email);
        Collection<String> groups = samlGroups(principal, config.getGroupsAttribute());
        return new SsoUserAttributes(principal.getName(), email, displayName, groups);
    }

    private static String attribute(Map<String, Object> attributes, String name) {
        Object value = attributes.get(name);
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    private static String samlAttribute(Saml2AuthenticatedPrincipal principal, String name) {
        List<Object> values = principal.getAttribute(name);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.getFirst().toString();
    }

    private static Collection<String> groups(Map<String, Object> attributes, String name) {
        Object value = attributes.get(name);
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(Object::toString).toList();
        }
        if (value != null) {
            return List.of(value.toString());
        }
        return List.of();
    }

    private static Collection<String> samlGroups(Saml2AuthenticatedPrincipal principal, String name) {
        List<Object> values = principal.getAttribute(name);
        if (values == null) {
            return List.of();
        }
        return values.stream().map(Object::toString).toList();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        throw new IllegalStateException("Required SSO attribute missing");
    }

    private record SsoUserAttributes(String subject, String email, String displayName, Collection<String> groups) {
    }
}

/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.sso;

import com.agentretry.manager.domain.IdentityProviderConfigEntity;
import com.agentretry.manager.domain.IdentityProviderType;
import com.agentretry.manager.sso.dto.SsoTestResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
public class SsoTestService {

    private final SsoConfigService ssoConfigService;
    private final RestClient restClient = RestClient.create();

    public SsoTestService(SsoConfigService ssoConfigService) {
        this.ssoConfigService = ssoConfigService;
    }

    public SsoTestResponse testConnection() {
        IdentityProviderConfigEntity config = ssoConfigService.requireConfig();
        if (config.getProviderType() == IdentityProviderType.OIDC) {
            return testOidc(config);
        }
        return testSaml(config);
    }

    private SsoTestResponse testOidc(IdentityProviderConfigEntity config) {
        try {
            String issuer = config.getIssuerUri().replaceAll("/$", "");
            String discoveryUrl = issuer + "/.well-known/openid-configuration";
            ResponseEntity<Map> response = restClient.get()
                    .uri(URI.create(discoveryUrl))
                    .retrieve()
                    .toEntity(Map.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return new SsoTestResponse(false, "Failed to fetch OIDC discovery document");
            }
            Object issuerClaim = response.getBody().get("issuer");
            if (issuerClaim == null) {
                return new SsoTestResponse(false, "OIDC discovery document missing issuer");
            }
            ssoConfigService.markValidated();
            return new SsoTestResponse(true, "OIDC discovery document validated");
        } catch (Exception ex) {
            return new SsoTestResponse(false, "OIDC test failed: " + ex.getMessage());
        }
    }

    private SsoTestResponse testSaml(IdentityProviderConfigEntity config) {
        try {
            String metadata = resolveSamlMetadata(config);
            Document document = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new ByteArrayInputStream(metadata.getBytes(StandardCharsets.UTF_8)));
            NodeList entities = document.getElementsByTagNameNS("*", "EntityDescriptor");
            if (entities.getLength() == 0) {
                entities = document.getElementsByTagName("EntityDescriptor");
            }
            if (entities.getLength() == 0) {
                return new SsoTestResponse(false, "SAML metadata missing EntityDescriptor");
            }
            ssoConfigService.markValidated();
            return new SsoTestResponse(true, "SAML metadata validated");
        } catch (Exception ex) {
            return new SsoTestResponse(false, "SAML test failed: " + ex.getMessage());
        }
    }

    private String resolveSamlMetadata(IdentityProviderConfigEntity config) {
        if (config.getIdpMetadataXml() != null && !config.getIdpMetadataXml().isBlank()) {
            return config.getIdpMetadataXml();
        }
        return restClient.get()
                .uri(URI.create(config.getIdpMetadataUrl()))
                .retrieve()
                .body(String.class);
    }
}

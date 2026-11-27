/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.sso;

import com.agentretry.manager.domain.IdentityProviderConfigEntity;
import com.agentretry.manager.domain.IdentityProviderType;
import com.agentretry.manager.security.SsoStateService;
import org.springframework.security.saml2.core.Saml2X509Credential;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

@Component
public class DatabaseRelyingPartyRegistrationRepository implements RelyingPartyRegistrationRepository {

    private final SsoStateService ssoStateService;
    private final RestClient restClient = RestClient.create();
    private volatile RelyingPartyRegistration cachedRegistration;

    public DatabaseRelyingPartyRegistrationRepository(SsoStateService ssoStateService) {
        this.ssoStateService = ssoStateService;
    }

    @Override
    public RelyingPartyRegistration findByRegistrationId(String registrationId) {
        if (!SsoConfigService.REGISTRATION_ID.equals(registrationId)) {
            return null;
        }
        RelyingPartyRegistration registration = cachedRegistration;
        if (registration != null) {
            return registration;
        }
        return refresh();
    }

    public synchronized RelyingPartyRegistration refresh() {
        cachedRegistration = loadFromDatabase();
        return cachedRegistration;
    }

    private RelyingPartyRegistration loadFromDatabase() {
        return ssoStateService.getActiveConfig()
                .filter(config -> config.getProviderType() == IdentityProviderType.SAML)
                .map(this::buildRegistration)
                .orElse(null);
    }

    private RelyingPartyRegistration buildRegistration(IdentityProviderConfigEntity config) {
        try {
            String metadata = resolveMetadata(config);
            SamlIdpMetadata idp = parseMetadata(metadata);
            return RelyingPartyRegistration.withRegistrationId(SsoConfigService.REGISTRATION_ID)
                    .entityId(config.getEntityId())
                    .assertionConsumerServiceLocation(config.getAcsUrl())
                    .assertingPartyDetails(party -> party
                            .entityId(idp.entityId())
                            .singleSignOnServiceLocation(idp.ssoUrl())
                            .wantAuthnRequestsSigned(false)
                            .verificationX509Credentials(credentials -> idp.certificates()
                                    .forEach(cert -> credentials.add(Saml2X509Credential.verification(cert))))
                    )
                    .build();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to build SAML relying party registration", ex);
        }
    }

    private String resolveMetadata(IdentityProviderConfigEntity config) {
        if (config.getIdpMetadataXml() != null && !config.getIdpMetadataXml().isBlank()) {
            return config.getIdpMetadataXml();
        }
        return restClient.get()
                .uri(URI.create(config.getIdpMetadataUrl()))
                .retrieve()
                .body(String.class);
    }

    private SamlIdpMetadata parseMetadata(String metadataXml) throws Exception {
        Document document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new ByteArrayInputStream(metadataXml.getBytes(StandardCharsets.UTF_8)));
        Element entity = (Element) document.getElementsByTagNameNS("*", "EntityDescriptor").item(0);
        if (entity == null) {
            entity = (Element) document.getElementsByTagName("EntityDescriptor").item(0);
        }
        if (entity == null) {
            throw new IllegalStateException("Missing EntityDescriptor in SAML metadata");
        }
        String entityId = entity.getAttribute("entityID");
        Element idpDescriptor = firstChildByLocalName(entity, "IDPSSODescriptor");
        String ssoUrl = null;
        NodeList ssoServices = idpDescriptor.getElementsByTagNameNS("*", "SingleSignOnService");
        if (ssoServices.getLength() == 0) {
            ssoServices = idpDescriptor.getElementsByTagName("SingleSignOnService");
        }
        for (int i = 0; i < ssoServices.getLength(); i++) {
            Element service = (Element) ssoServices.item(i);
            if ("urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect".equals(service.getAttribute("Binding"))
                    || ssoUrl == null) {
                ssoUrl = service.getAttribute("Location");
            }
        }
        List<X509Certificate> certificates = new ArrayList<>();
        NodeList certNodes = idpDescriptor.getElementsByTagNameNS("*", "X509Certificate");
        if (certNodes.getLength() == 0) {
            certNodes = idpDescriptor.getElementsByTagName("X509Certificate");
        }
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        for (int i = 0; i < certNodes.getLength(); i++) {
            String encoded = certNodes.item(i).getTextContent().replaceAll("\\s+", "");
            byte[] decoded = java.util.Base64.getDecoder().decode(encoded);
            certificates.add((X509Certificate) factory.generateCertificate(new ByteArrayInputStream(decoded)));
        }
        return new SamlIdpMetadata(entityId, ssoUrl, certificates);
    }

    private static Element firstChildByLocalName(Element parent, String localName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element element && localName.equals(element.getLocalName())) {
                return element;
            }
        }
        throw new IllegalStateException("Missing " + localName + " in SAML metadata");
    }

    private record SamlIdpMetadata(String entityId, String ssoUrl, List<X509Certificate> certificates) {
    }
}

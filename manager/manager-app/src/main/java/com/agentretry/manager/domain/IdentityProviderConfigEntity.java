/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "identity_provider_config")
public class IdentityProviderConfigEntity {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false)
    private IdentityProviderType providerType;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "validated_at")
    private Instant validatedAt;

    @Column(name = "issuer_uri")
    private String issuerUri;

    @Column(name = "client_id")
    private String clientId;

    @Column(name = "client_secret_enc")
    private String clientSecretEnc;

    @Column(name = "scopes")
    private String scopes;

    @Column(name = "redirect_uri")
    private String redirectUri;

    @Column(name = "idp_metadata_url")
    private String idpMetadataUrl;

    @Column(name = "idp_metadata_xml")
    private String idpMetadataXml;

    @Column(name = "entity_id")
    private String entityId;

    @Column(name = "acs_url")
    private String acsUrl;

    @Column(name = "email_attribute")
    private String emailAttribute;

    @Column(name = "display_name_attribute")
    private String displayNameAttribute;

    @Column(name = "groups_attribute")
    private String groupsAttribute;

    @Column(name = "superuser_group")
    private String superuserGroup;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public IdentityProviderType getProviderType() {
        return providerType;
    }

    public void setProviderType(IdentityProviderType providerType) {
        this.providerType = providerType;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getValidatedAt() {
        return validatedAt;
    }

    public void setValidatedAt(Instant validatedAt) {
        this.validatedAt = validatedAt;
    }

    public String getIssuerUri() {
        return issuerUri;
    }

    public void setIssuerUri(String issuerUri) {
        this.issuerUri = issuerUri;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecretEnc() {
        return clientSecretEnc;
    }

    public void setClientSecretEnc(String clientSecretEnc) {
        this.clientSecretEnc = clientSecretEnc;
    }

    public String getScopes() {
        return scopes;
    }

    public void setScopes(String scopes) {
        this.scopes = scopes;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public String getIdpMetadataUrl() {
        return idpMetadataUrl;
    }

    public void setIdpMetadataUrl(String idpMetadataUrl) {
        this.idpMetadataUrl = idpMetadataUrl;
    }

    public String getIdpMetadataXml() {
        return idpMetadataXml;
    }

    public void setIdpMetadataXml(String idpMetadataXml) {
        this.idpMetadataXml = idpMetadataXml;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getAcsUrl() {
        return acsUrl;
    }

    public void setAcsUrl(String acsUrl) {
        this.acsUrl = acsUrl;
    }

    public String getEmailAttribute() {
        return emailAttribute;
    }

    public void setEmailAttribute(String emailAttribute) {
        this.emailAttribute = emailAttribute;
    }

    public String getDisplayNameAttribute() {
        return displayNameAttribute;
    }

    public void setDisplayNameAttribute(String displayNameAttribute) {
        this.displayNameAttribute = displayNameAttribute;
    }

    public String getGroupsAttribute() {
        return groupsAttribute;
    }

    public void setGroupsAttribute(String groupsAttribute) {
        this.groupsAttribute = groupsAttribute;
    }

    public String getSuperuserGroup() {
        return superuserGroup;
    }

    public void setSuperuserGroup(String superuserGroup) {
        this.superuserGroup = superuserGroup;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

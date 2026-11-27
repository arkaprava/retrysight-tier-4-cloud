/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

export type SsoStatus = {
  ssoEnabled: boolean
  disasterRecovery: boolean
  localLoginAvailable: boolean
  providerType: 'OIDC' | 'SAML' | null
  ssoLoginUrl: string | null
}

export type SsoConfig = {
  id: string | null
  providerType: 'OIDC' | 'SAML'
  enabled: boolean
  validatedAt: string | null
  issuerUri: string | null
  clientId: string | null
  clientSecretSet: boolean
  scopes: string | null
  redirectUri: string | null
  idpMetadataUrl: string | null
  idpMetadataXmlSet: boolean
  entityId: string | null
  acsUrl: string | null
  emailAttribute: string | null
  displayNameAttribute: string | null
  groupsAttribute: string | null
  superuserGroup: string | null
  createdBy: string | null
  updatedAt: string | null
}

export type SsoConfigInput = {
  providerType: 'OIDC' | 'SAML'
  issuerUri?: string
  clientId?: string
  clientSecret?: string
  scopes?: string
  idpMetadataUrl?: string
  idpMetadataXml?: string
  emailAttribute?: string
  displayNameAttribute?: string
  groupsAttribute?: string
  superuserGroup?: string
}

export type SsoTestResult = {
  success: boolean
  message: string
}

export async function fetchSsoStatus(): Promise<SsoStatus> {
  const response = await fetch('/api/v1/auth/sso/status')
  if (!response.ok) {
    throw new Error('Failed to load SSO status')
  }
  return response.json()
}

function authHeaders(token: string): HeadersInit {
  return {
    Authorization: `Bearer ${token}`,
    'Content-Type': 'application/json',
  }
}

export async function fetchSsoConfig(token: string): Promise<SsoConfig | null> {
  const response = await fetch('/api/v1/admin/sso/config', {
    headers: authHeaders(token),
  })
  if (response.status === 404) {
    return null
  }
  if (!response.ok) {
    throw new Error('Failed to load SSO configuration')
  }
  const text = await response.text()
  return text ? JSON.parse(text) : null
}

export async function saveSsoConfig(token: string, input: SsoConfigInput): Promise<SsoConfig> {
  const response = await fetch('/api/v1/admin/sso/config', {
    method: 'PUT',
    headers: authHeaders(token),
    body: JSON.stringify(input),
  })
  if (!response.ok) {
    const message = await response.text()
    throw new Error(message || 'Failed to save SSO configuration')
  }
  return response.json()
}

export async function testSsoConnection(token: string): Promise<SsoTestResult> {
  const response = await fetch('/api/v1/admin/sso/test', {
    method: 'POST',
    headers: authHeaders(token),
  })
  if (!response.ok) {
    throw new Error('Failed to run SSO connection test')
  }
  return response.json()
}

export async function enableSso(token: string): Promise<SsoConfig> {
  const response = await fetch('/api/v1/admin/sso/enable', {
    method: 'POST',
    headers: authHeaders(token),
  })
  if (!response.ok) {
    const message = await response.text()
    throw new Error(message || 'Failed to enable SSO')
  }
  return response.json()
}

export async function deleteSsoConfig(token: string): Promise<void> {
  const response = await fetch('/api/v1/admin/sso/config', {
    method: 'DELETE',
    headers: authHeaders(token),
  })
  if (!response.ok) {
    throw new Error('Failed to delete SSO configuration')
  }
}

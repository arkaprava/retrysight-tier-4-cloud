/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import { FormEvent, useEffect, useState } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import {
  deleteSsoConfig,
  enableSso,
  fetchSsoConfig,
  saveSsoConfig,
  testSsoConnection,
  type SsoConfig,
  type SsoConfigInput,
} from '../ssoApi'

type WizardStep = 1 | 2 | 3 | 4 | 5

const DEFAULT_SCOPES = 'openid profile email'

export default function SecuritySettingsPage() {
  const { token, isSecadmin } = useAuth()
  const [step, setStep] = useState<WizardStep>(1)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [testing, setTesting] = useState(false)
  const [enabling, setEnabling] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)
  const [config, setConfig] = useState<SsoConfig | null>(null)

  const [providerType, setProviderType] = useState<'OIDC' | 'SAML'>('OIDC')
  const [issuerUri, setIssuerUri] = useState('')
  const [clientId, setClientId] = useState('')
  const [clientSecret, setClientSecret] = useState('')
  const [scopes, setScopes] = useState(DEFAULT_SCOPES)
  const [idpMetadataUrl, setIdpMetadataUrl] = useState('')
  const [idpMetadataXml, setIdpMetadataXml] = useState('')
  const [emailAttribute, setEmailAttribute] = useState('email')
  const [displayNameAttribute, setDisplayNameAttribute] = useState('name')
  const [groupsAttribute, setGroupsAttribute] = useState('groups')
  const [superuserGroup, setSuperuserGroup] = useState('')

  useEffect(() => {
    if (!token) return
    let cancelled = false
    async function load() {
      setLoading(true)
      setError(null)
      try {
        const loaded = await fetchSsoConfig(token as string)
        if (!cancelled && loaded) {
          applyConfig(loaded)
          setConfig(loaded)
          if (loaded.enabled) {
            setStep(5)
          } else if (loaded.validatedAt) {
            setStep(4)
          } else if (loaded.id || loaded.issuerUri || loaded.idpMetadataUrl) {
            setStep(3)
          }
        }
      } catch (loadError) {
        if (!cancelled) {
          setError(loadError instanceof Error ? loadError.message : 'Failed to load configuration')
        }
      } finally {
        if (!cancelled) {
          setLoading(false)
        }
      }
    }
    void load()
    return () => {
      cancelled = true
    }
  }, [token])

  if (!isSecadmin) {
    return <Navigate to="/" replace />
  }

  function applyConfig(loaded: SsoConfig) {
    setProviderType(loaded.providerType)
    setIssuerUri(loaded.issuerUri ?? '')
    setClientId(loaded.clientId ?? '')
    setScopes(loaded.scopes ?? DEFAULT_SCOPES)
    setIdpMetadataUrl(loaded.idpMetadataUrl ?? '')
    setEmailAttribute(loaded.emailAttribute ?? 'email')
    setDisplayNameAttribute(loaded.displayNameAttribute ?? 'name')
    setGroupsAttribute(loaded.groupsAttribute ?? 'groups')
    setSuperuserGroup(loaded.superuserGroup ?? '')
  }

  function buildInput(): SsoConfigInput {
    return {
      providerType,
      issuerUri: issuerUri.trim(),
      clientId: clientId.trim(),
      clientSecret: clientSecret.trim() || undefined,
      scopes: scopes.trim(),
      idpMetadataUrl: idpMetadataUrl.trim(),
      idpMetadataXml: idpMetadataXml.trim() || undefined,
      emailAttribute: emailAttribute.trim(),
      displayNameAttribute: displayNameAttribute.trim(),
      groupsAttribute: groupsAttribute.trim(),
      superuserGroup: superuserGroup.trim(),
    }
  }

  async function handleSave(e?: FormEvent) {
    e?.preventDefault()
    if (!token) return
    setSaving(true)
    setError(null)
    setSuccess(null)
    try {
      const saved = await saveSsoConfig(token, buildInput())
      setConfig(saved)
      applyConfig(saved)
      setClientSecret('')
      setSuccess('Configuration saved. Run a connection test before enabling SSO.')
      setStep(4)
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : 'Failed to save configuration')
    } finally {
      setSaving(false)
    }
  }

  async function handleTest() {
    if (!token) return
    setTesting(true)
    setError(null)
    setSuccess(null)
    try {
      const result = await testSsoConnection(token)
      if (!result.success) {
        setError(result.message)
        return
      }
      const refreshed = await fetchSsoConfig(token)
      if (refreshed) {
        setConfig(refreshed)
      }
      setSuccess(result.message)
      setStep(5)
    } catch (testError) {
      setError(testError instanceof Error ? testError.message : 'Connection test failed')
    } finally {
      setTesting(false)
    }
  }

  async function handleEnable() {
    if (!token) return
    if (!window.confirm('Enable SSO? Local login will be disabled for all users except disaster-recovery secadmin.')) {
      return
    }
    setEnabling(true)
    setError(null)
    setSuccess(null)
    try {
      const enabled = await enableSso(token)
      setConfig(enabled)
      setSuccess('SSO is now enabled.')
    } catch (enableError) {
      setError(enableError instanceof Error ? enableError.message : 'Failed to enable SSO')
    } finally {
      setEnabling(false)
    }
  }

  async function handleDelete() {
    if (!token) return
    if (!window.confirm('Delete SSO configuration? This enables disaster-recovery secadmin login.')) {
      return
    }
    setError(null)
    setSuccess(null)
    try {
      await deleteSsoConfig(token)
      setConfig(null)
      setStep(1)
      setSuccess('SSO configuration removed. Disaster recovery mode is active.')
    } catch (deleteError) {
      setError(deleteError instanceof Error ? deleteError.message : 'Failed to delete configuration')
    }
  }

  return (
    <div className="security-page">
      <header className="page-header">
        <div>
          <h2>Security Settings</h2>
          <p className="page-subtitle">Configure OIDC or SAML single sign-on for the manager dashboard.</p>
        </div>
      </header>

      <div className="security-wizard-steps" aria-label="Setup progress">
        {(['Provider', 'Identity Provider', 'Attributes', 'Test', 'Enable'] as const).map((label, index) => {
          const stepNumber = (index + 1) as WizardStep
          return (
            <button
              key={label}
              type="button"
              className={`security-step${step === stepNumber ? ' active' : ''}${step > stepNumber ? ' complete' : ''}`}
              onClick={() => setStep(stepNumber)}
            >
              <span>{stepNumber}</span>
              {label}
            </button>
          )
        })}
      </div>

      {loading ? (
        <p className="login-hint">Loading configuration…</p>
      ) : (
        <form className="security-form" onSubmit={handleSave}>
          {step === 1 && (
            <section className="security-panel">
              <h3>Choose provider type</h3>
              <div className="security-provider-tabs">
                <button
                  type="button"
                  className={providerType === 'OIDC' ? 'active' : ''}
                  onClick={() => setProviderType('OIDC')}
                >
                  OIDC
                </button>
                <button
                  type="button"
                  className={providerType === 'SAML' ? 'active' : ''}
                  onClick={() => setProviderType('SAML')}
                >
                  SAML
                </button>
              </div>
              <p className="login-hint">Only one provider can be active per deployment.</p>
              <button type="button" className="security-next-btn" onClick={() => setStep(2)}>
                Continue
              </button>
            </section>
          )}

          {step === 2 && providerType === 'OIDC' && (
            <section className="security-panel">
              <h3>OIDC identity provider</h3>
              <label>
                Issuer URI
                <input value={issuerUri} onChange={(e) => setIssuerUri(e.target.value)} placeholder="https://login.microsoftonline.com/{tenant}/v2.0" required />
              </label>
              <label>
                Client ID
                <input value={clientId} onChange={(e) => setClientId(e.target.value)} required />
              </label>
              <label>
                Client Secret
                <input
                  value={clientSecret}
                  onChange={(e) => setClientSecret(e.target.value)}
                  type="password"
                  placeholder={config?.clientSecretSet ? 'Leave blank to keep existing secret' : ''}
                />
              </label>
              <label>
                Scopes
                <input value={scopes} onChange={(e) => setScopes(e.target.value)} />
              </label>
              {config?.redirectUri && (
                <label>
                  Redirect URI (read-only)
                  <input value={config.redirectUri} readOnly />
                </label>
              )}
              <div className="security-actions">
                <button type="button" onClick={() => setStep(1)}>Back</button>
                <button type="button" onClick={() => setStep(3)}>Continue</button>
              </div>
            </section>
          )}

          {step === 2 && providerType === 'SAML' && (
            <section className="security-panel">
              <h3>SAML identity provider</h3>
              <label>
                IdP Metadata URL
                <input value={idpMetadataUrl} onChange={(e) => setIdpMetadataUrl(e.target.value)} placeholder="https://idp.example.com/metadata" />
              </label>
              <label>
                Or paste metadata XML
                <textarea value={idpMetadataXml} onChange={(e) => setIdpMetadataXml(e.target.value)} rows={8} />
              </label>
              {config?.entityId && (
                <label>
                  SP Entity ID (read-only)
                  <input value={config.entityId} readOnly />
                </label>
              )}
              {config?.acsUrl && (
                <label>
                  ACS URL (read-only)
                  <input value={config.acsUrl} readOnly />
                </label>
              )}
              <div className="security-actions">
                <button type="button" onClick={() => setStep(1)}>Back</button>
                <button type="button" onClick={() => setStep(3)}>Continue</button>
              </div>
            </section>
          )}

          {step === 3 && (
            <section className="security-panel">
              <h3>Attribute mapping</h3>
              <label>
                Email attribute
                <input value={emailAttribute} onChange={(e) => setEmailAttribute(e.target.value)} required />
              </label>
              <label>
                Display name attribute
                <input value={displayNameAttribute} onChange={(e) => setDisplayNameAttribute(e.target.value)} required />
              </label>
              <label>
                Groups attribute
                <input value={groupsAttribute} onChange={(e) => setGroupsAttribute(e.target.value)} required />
              </label>
              <label>
                Superuser group (grants SECADMIN)
                <input value={superuserGroup} onChange={(e) => setSuperuserGroup(e.target.value)} required />
              </label>
              <div className="security-actions">
                <button type="button" onClick={() => setStep(2)}>Back</button>
                <button type="submit" disabled={saving}>{saving ? 'Saving…' : 'Save configuration'}</button>
              </div>
            </section>
          )}

          {step === 4 && (
            <section className="security-panel">
              <h3>Test connection</h3>
              <p className="login-hint">
                Validate connectivity to the identity provider before enabling SSO.
              </p>
              {config?.validatedAt && (
                <p className="security-success">Last validated: {new Date(config.validatedAt).toLocaleString()}</p>
              )}
              <div className="security-actions">
                <button type="button" onClick={() => setStep(3)}>Back</button>
                <button type="button" onClick={() => void handleTest()} disabled={testing}>
                  {testing ? 'Testing…' : 'Test connection'}
                </button>
              </div>
            </section>
          )}

          {step === 5 && (
            <section className="security-panel">
              <h3>Enable SSO</h3>
              {config?.enabled ? (
                <p className="security-success">SSO is enabled. Local login is disabled except disaster-recovery secadmin.</p>
              ) : (
                <p className="login-hint">
                  Enabling SSO permanently disables local login for all users. Ensure the connection test succeeded and superuser group is set.
                </p>
              )}
              <div className="security-actions">
                <button type="button" onClick={() => setStep(4)}>Back</button>
                {!config?.enabled && (
                  <button type="button" onClick={() => void handleEnable()} disabled={enabling || !config?.validatedAt}>
                    {enabling ? 'Enabling…' : 'Enable SSO'}
                  </button>
                )}
                {config && (
                  <button type="button" className="security-danger-btn" onClick={() => void handleDelete()}>
                    Delete configuration
                  </button>
                )}
              </div>
            </section>
          )}
        </form>
      )}

      {error && <p className="login-error">{error}</p>}
      {success && <p className="security-success">{success}</p>}
    </div>
  )
}

/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import { useEffect, useState } from 'react'
import { Navigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

export default function AuthCallbackPage() {
  const { login, token } = useAuth()
  const [params] = useSearchParams()
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const code = params.get('code')
    const legacyToken = params.get('token')
    const email = params.get('email')
    const displayName = params.get('displayName')
    const rolesParam = params.get('roles')

    if (legacyToken && email && displayName) {
      const roles = rolesParam
        ? rolesParam.split(',').map((role) => role.trim()).filter(Boolean)
        : ['UI']
      login(legacyToken, email, displayName, roles)
      return
    }

    if (!code) {
      return
    }

    let cancelled = false
    ;(async () => {
      try {
        const response = await fetch('/api/v1/auth/sso/exchange', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ code }),
        })
        if (!response.ok) {
          const body = await response.json().catch(() => ({}))
          throw new Error(body.error ?? 'SSO sign-in failed')
        }
        const payload = await response.json()
        if (cancelled) {
          return
        }
        login(
          payload.token,
          payload.email,
          payload.displayName,
          payload.roles ?? ['UI'],
        )
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : 'SSO sign-in failed')
        }
      }
    })()

    return () => {
      cancelled = true
    }
  }, [login, params])

  if (token) {
    return <Navigate to="/" replace />
  }

  if (error) {
    return <Navigate to="/login" replace state={{ error }} />
  }

  const code = params.get('code')
  const legacyToken = params.get('token')
  if (!code && !legacyToken) {
    return <Navigate to="/login" replace />
  }

  return (
    <div className="login-page">
      <div className="login-shell login-shell--centered">
        <main className="login-panel">
          <div className="login-card">
            <div className="login-status" role="status">
              <div className="loading-spinner login-status-spinner" aria-hidden="true" />
              <p className="login-card-subtitle">Completing sign-in…</p>
            </div>
          </div>
        </main>
      </div>
    </div>
  )
}

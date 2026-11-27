/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import { FormEvent, useState } from 'react'
import { Navigate } from 'react-router-dom'
import { useMutation, useQuery } from '@apollo/client'
import { useAuth } from '../auth/AuthContext'
import {
  API_KEYS,
  CREATE_API_KEY,
  REVOKE_API_KEY,
  ROTATE_API_KEY,
} from '../graphql/client'

export default function ApiKeysPage() {
  const { isSecadmin } = useAuth()
  const { data, loading, refetch } = useQuery(API_KEYS, { skip: !isSecadmin })
  const [createKey] = useMutation(CREATE_API_KEY, { onCompleted: () => refetch() })
  const [revokeKey] = useMutation(REVOKE_API_KEY, { onCompleted: () => refetch() })
  const [rotateKey] = useMutation(ROTATE_API_KEY, { onCompleted: () => refetch() })
  const [name, setName] = useState('')
  const [role, setRole] = useState('AGENT')
  const [rawKey, setRawKey] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  if (!isSecadmin) {
    return <Navigate to="/" replace />
  }

  async function handleCreate(event: FormEvent) {
    event.preventDefault()
    setError(null)
    try {
      const result = await createKey({ variables: { name, role } })
      setRawKey(result.data?.createApiKey?.rawKey ?? null)
      setName('')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Create failed')
    }
  }

  return (
    <div className="security-page">
      <header className="page-header">
        <h2>API Keys</h2>
        <p className="page-subtitle">Create, rotate, and revoke agent ingest API keys</p>
      </header>

      <section className="security-panel">
        <form className="cost-billing-form" onSubmit={handleCreate}>
          <input
            placeholder="Key name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
          />
          <select value={role} onChange={(e) => setRole(e.target.value)}>
            <option value="AGENT">AGENT</option>
            <option value="UI">UI</option>
          </select>
          <button type="submit">Create key</button>
        </form>
        {rawKey && (
          <p className="security-success">
            Copy this key now — it will not be shown again: <code>{rawKey}</code>
          </p>
        )}
        {error && <p className="login-error">{error}</p>}
      </section>

      <section className="security-panel">
        {loading ? (
          <p className="empty">Loading…</p>
        ) : (
          <table className="data-table">
            <thead>
              <tr>
                <th>Name</th>
                <th>Prefix</th>
                <th>Role</th>
                <th>Active</th>
                <th>Last used</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {(data?.apiKeys ?? []).map((key: any) => (
                <tr key={key.id}>
                  <td>{key.name}</td>
                  <td><code>{key.keyPrefix ?? '—'}</code></td>
                  <td>{key.role}</td>
                  <td>{key.active ? 'Yes' : 'No'}</td>
                  <td>{key.lastUsedAt ? new Date(key.lastUsedAt).toLocaleString() : '—'}</td>
                  <td>
                    {key.active && (
                      <>
                        <button
                          type="button"
                          onClick={async () => {
                            const result = await rotateKey({ variables: { id: key.id } })
                            setRawKey(result.data?.rotateApiKey?.rawKey ?? null)
                          }}
                        >
                          Rotate
                        </button>
                        <button type="button" onClick={() => revokeKey({ variables: { id: key.id } })}>
                          Revoke
                        </button>
                      </>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  )
}

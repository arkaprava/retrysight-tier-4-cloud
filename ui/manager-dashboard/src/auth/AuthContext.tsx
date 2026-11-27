/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import { createContext, useContext, useMemo, useState, ReactNode } from 'react'

type AuthContextValue = {
  token: string | null
  email: string | null
  displayName: string | null
  roles: string[]
  /** Ops admin (billing / contract review). Not the superuser. */
  isAdmin: boolean
  /**
   * Security admin — platform superuser. Can see and use every UI page,
   * including Security / API Keys / Archive and all ADMIN surfaces.
   */
  isSecadmin: boolean
  /** Alias of {@link AuthContextValue.isSecadmin}. */
  isSuperuser: boolean
  /** ADMIN or SECADMIN — contract upload / review (not apply by itself). */
  canReviewContracts: boolean
  login: (token: string, email: string, displayName: string, roles?: string[]) => void
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

function parseStoredRoles(): string[] {
  const raw = localStorage.getItem('authRoles')
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed.map(String) : []
  } catch {
    return raw.split(',').map((role) => role.trim()).filter(Boolean)
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(localStorage.getItem('authToken'))
  const [email, setEmail] = useState<string | null>(localStorage.getItem('authEmail'))
  const [displayName, setDisplayName] = useState<string | null>(localStorage.getItem('authDisplayName'))
  const [roles, setRoles] = useState<string[]>(parseStoredRoles())

  const value = useMemo(() => ({
    token,
    email,
    displayName,
    roles,
    isAdmin: roles.includes('ADMIN'),
    isSecadmin: roles.includes('SECADMIN'),
    isSuperuser: roles.includes('SECADMIN'),
    canReviewContracts: roles.includes('ADMIN') || roles.includes('SECADMIN'),
    login: (newToken: string, newEmail: string, name: string, newRoles: string[] = ['UI']) => {
      localStorage.setItem('authToken', newToken)
      localStorage.setItem('authEmail', newEmail)
      localStorage.setItem('authDisplayName', name)
      localStorage.setItem('authRoles', JSON.stringify(newRoles))
      setToken(newToken)
      setEmail(newEmail)
      setDisplayName(name)
      setRoles(newRoles)
    },
    logout: () => {
      localStorage.removeItem('authToken')
      localStorage.removeItem('authEmail')
      localStorage.removeItem('authDisplayName')
      localStorage.removeItem('authRoles')
      setToken(null)
      setEmail(null)
      setDisplayName(null)
      setRoles([])
    },
  }), [token, email, displayName, roles])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) {
    throw new Error('useAuth must be used within AuthProvider')
  }
  return ctx
}

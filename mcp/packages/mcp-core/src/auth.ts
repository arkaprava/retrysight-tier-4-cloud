/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import type { McpConfig } from './config.js'
import { ManagerError, toFriendlyError } from './errors.js'

export type AuthPayload = {
  token: string
  email: string
  displayName: string
  roles: string[]
}

export type McpTokenPayload = {
  token: string
  email: string
  expiresAt: string
}

export class AuthClient {
  private cachedToken?: string

  constructor(private readonly config: McpConfig) {}

  async getToken(): Promise<string> {
    if (this.config.token) {
      return this.config.token
    }
    if (this.cachedToken) {
      return this.cachedToken
    }
    if (!this.config.email || !this.config.password) {
      throw new ManagerError(
        'No APM_MCP_TOKEN configured and APM_EMAIL / APM_PASSWORD are missing.',
        'CONFIG',
      )
    }

    try {
      const response = await fetch(`${this.config.managerUrl}/api/v1/auth/mcp-token`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: this.config.email, password: this.config.password }),
      })
      if (!response.ok) {
        throw new ManagerError(
          `MCP token request failed (${response.status}). Verify credentials.`,
          'AUTH',
        )
      }
      const payload = (await response.json()) as McpTokenPayload
      this.cachedToken = payload.token
      return payload.token
    } catch (error) {
      throw toFriendlyError(error)
    }
  }

  clearCache(): void {
    this.cachedToken = undefined
  }
}

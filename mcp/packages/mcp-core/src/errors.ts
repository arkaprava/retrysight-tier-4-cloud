/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

export class ManagerError extends Error {
  constructor(
    message: string,
    readonly code: 'AUTH' | 'NETWORK' | 'GRAPHQL' | 'HTTP' | 'CONFIG',
    readonly cause?: unknown,
  ) {
    super(message)
    this.name = 'ManagerError'
  }
}

export function toFriendlyError(error: unknown): ManagerError {
  if (error instanceof ManagerError) {
    return error
  }

  if (error instanceof TypeError && String(error.message).includes('fetch')) {
    return new ManagerError(
      'Cannot reach the Agent Performance Monitor manager. Check MANAGER_URL and ensure the manager is running.',
      'NETWORK',
      error,
    )
  }

  const message = error instanceof Error ? error.message : String(error)
  if (message.includes('401') || message.toLowerCase().includes('unauthorized')) {
    return new ManagerError(
      'Authentication failed. Refresh APM_MCP_TOKEN or verify APM_EMAIL / APM_PASSWORD.',
      'AUTH',
      error,
    )
  }

  if (message.includes('403')) {
    return new ManagerError(
      'Access denied. Your token may lack UI, ADMIN, or SECADMIN role required for read queries.',
      'AUTH',
      error,
    )
  }

  return new ManagerError(message, 'HTTP', error)
}

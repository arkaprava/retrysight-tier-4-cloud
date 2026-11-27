/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

export type McpConfig = {
  managerUrl: string
  token?: string
  email?: string
  password?: string
}

export function loadConfig(env: NodeJS.ProcessEnv = process.env): McpConfig {
  const managerPort = env.MANAGER_PORT ?? '28477'
  const managerUrl = (
    env.MANAGER_URL ??
    env.APM_MANAGER_URL ??
    `http://localhost:${managerPort}`
  ).replace(/\/$/, '')
  const token = env.APM_MCP_TOKEN ?? env.APM_TOKEN
  const email = env.APM_EMAIL
  const password = env.APM_PASSWORD

  if (!token && (!email || !password)) {
    throw new Error(
      'Missing MCP credentials. Set APM_MCP_TOKEN or both APM_EMAIL and APM_PASSWORD.',
    )
  }

  return { managerUrl, token, email, password }
}

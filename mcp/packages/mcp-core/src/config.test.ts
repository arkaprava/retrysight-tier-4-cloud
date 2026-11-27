/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

import assert from 'node:assert/strict'
import test from 'node:test'
import { loadConfig } from './config.js'
import { toFriendlyError, ManagerError } from './errors.js'
import { zodToJsonSchema } from './framework.js'
import { z } from 'zod'

test('loadConfig accepts token-only auth', () => {
  const config = loadConfig({
    MANAGER_URL: 'http://localhost:28477/',
    APM_MCP_TOKEN: 'abc',
  })
  assert.equal(config.managerUrl, 'http://localhost:28477')
  assert.equal(config.token, 'abc')
})

test('loadConfig accepts email and password', () => {
  const config = loadConfig({
    APM_EMAIL: 'secadmin@local',
    APM_PASSWORD: 'changeme',
  })
  assert.equal(config.email, 'secadmin@local')
  assert.equal(config.password, 'changeme')
})

test('loadConfig rejects missing credentials', () => {
  assert.throws(() => loadConfig({}), /Missing MCP credentials/)
})

test('toFriendlyError maps network failures', () => {
  const err = toFriendlyError(new TypeError('fetch failed'))
  assert.equal(err.code, 'NETWORK')
})

test('zodToJsonSchema builds object schema', () => {
  const schema = z.object({
    user: z.string().optional(),
    page: z.number(),
  })
  const json = zodToJsonSchema(schema)
  assert.equal(json.type, 'object')
  assert.deepEqual(json.required, ['page'])
})

#!/usr/bin/env node
/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

// SPDX-License-Identifier: Apache-2.0

import { spawn } from 'node:child_process'
import { readFileSync, unlinkSync } from 'node:fs'
import { homedir, tmpdir } from 'node:os'
import { join } from 'node:path'
import { randomUUID } from 'node:crypto'
import { CLOUDFLARE_ACCOUNT_ID, R2_BUCKET_PUBLIC } from './cdn-release-paths.mjs'

function wranglerOAuthToken() {
  if (process.env.CLOUDFLARE_API_TOKEN) {
    return process.env.CLOUDFLARE_API_TOKEN
  }
  const configPath =
    process.env.WRANGLER_CONFIG ||
    join(homedir(), 'Library/Preferences/.wrangler/config/default.toml')
  try {
    const match = readFileSync(configPath, 'utf8').match(/^oauth_token\s*=\s*"([^"]+)"/m)
    return match?.[1] || null
  } catch {
    return null
  }
}

export async function fetchJson(url) {
  const response = await fetch(url, { redirect: 'follow' })
  if (response.status === 404) return null
  if (!response.ok) {
    throw new Error(`HTTP ${response.status} fetching ${url}`)
  }
  return response.json()
}

export function wranglerDelete(objectKey, { bucket = R2_BUCKET_PUBLIC, dryRun = false } = {}) {
  if (dryRun) {
    console.log(`  [dry-run] delete ${bucket}/${objectKey}`)
    return Promise.resolve()
  }

  return new Promise((resolve, reject) => {
    const child = spawn(
      'npx',
      ['wrangler', 'r2', 'object', 'delete', `${bucket}/${objectKey}`, '--remote'],
      { stdio: 'inherit' },
    )
    child.on('error', reject)
    child.on('close', (code) => {
      if (code === 0) resolve()
      else reject(new Error(`delete failed for ${objectKey} (exit ${code})`))
    })
  })
}

/**
 * List object keys under a prefix via Cloudflare API.
 * Requires CLOUDFLARE_ACCOUNT_ID and CLOUDFLARE_API_TOKEN (or wrangler OAuth token).
 */
export async function listR2ObjectKeys(prefix, { bucket = R2_BUCKET_PUBLIC } = {}) {
  const accountId = CLOUDFLARE_ACCOUNT_ID
  const token = wranglerOAuthToken()
  if (!accountId || !token) {
    throw new Error(
      'R2 list requires CLOUDFLARE_ACCOUNT_ID and CLOUDFLARE_API_TOKEN (or wrangler login)',
    )
  }

  const keys = []
  let cursor = undefined

  do {
    const url = new URL(
      `https://api.cloudflare.com/client/v4/accounts/${accountId}/r2/buckets/${bucket}/objects`,
    )
    url.searchParams.set('prefix', prefix)
    url.searchParams.set('per_page', '1000')
    if (cursor) url.searchParams.set('cursor', cursor)

    const response = await fetch(url, {
      headers: { Authorization: `Bearer ${token}` },
    })

    const payload = await response.json()
    if (!response.ok || !payload.success) {
      const message = payload.errors?.[0]?.message || response.statusText
      throw new Error(`R2 list failed: ${message}`)
    }

    for (const object of payload.result || []) {
      keys.push(object.key)
    }

    const info = payload.result_info
    cursor = info?.is_truncated ? info?.cursor : undefined
  } while (cursor)

  return keys
}

export async function fetchR2Json(objectKey, { bucket = R2_BUCKET_PUBLIC } = {}) {
  const tmpFile = join(tmpdir(), `r2-${randomUUID()}.json`)

  return new Promise((resolve, reject) => {
    const child = spawn(
      'npx',
      [
        'wrangler',
        'r2',
        'object',
        'get',
        `${bucket}/${objectKey}`,
        '--remote',
        '--file',
        tmpFile,
      ],
      { stdio: 'inherit' },
    )

    child.on('error', reject)
    child.on('close', (code) => {
      try {
        if (code !== 0) {
          resolve(null)
          return
        }
        const text = readFileSync(tmpFile, 'utf8')
        resolve(JSON.parse(text))
      } catch (err) {
        reject(err)
      } finally {
        try {
          unlinkSync(tmpFile)
        } catch {
          // ignore
        }
      }
    })
  })
}

export async function deleteAllUnderPrefix(prefix, { bucket = R2_BUCKET_PUBLIC, dryRun = false } = {}) {
  const keys = await listR2ObjectKeys(prefix, { bucket })
  if (keys.length === 0) {
    console.log(`  (no objects under ${bucket}/${prefix})`)
    return 0
  }

  console.log(`Deleting ${keys.length} object(s) from ${bucket}/${prefix}`)
  for (const key of keys) {
    console.log(`  - ${key}`)
    await wranglerDelete(key, { bucket, dryRun })
  }
  return keys.length
}

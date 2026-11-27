#!/usr/bin/env node
/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

// SPDX-License-Identifier: Apache-2.0

/**
 * Upload staged enterprise agent artifacts to Cloudflare R2.
 *
 * Reads release/cdn-upload-manifest.json (written by stage-agent-cdn.mjs).
 *
 * Usage:
 *   node scripts/ci/upload-agent-r2.mjs
 *
 * Env:
 *   R2_BUCKET — public bucket override (tier-core only; tiers 2–4 use R2_BUCKET_PRIVATE)
 */

import { spawn } from 'node:child_process'
import { createReadStream, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'
import {
  enterpriseManifestUrl,
  r2BucketForTier,
  tierIsPublicCdn,
} from './cdn-release-paths.mjs'

const manifestPath = join(process.cwd(), 'release/cdn-upload-manifest.json')

function fmtBytes(n) {
  if (n >= 1e9) return `${(n / 1e9).toFixed(1)} GB`
  if (n >= 1e6) return `${(n / 1e6).toFixed(1)} MB`
  if (n >= 1e3) return `${(n / 1e3).toFixed(1)} KB`
  return `${n} B`
}

function upload({ key, file, contentType, bucket }) {
  const size = statSync(file).size
  const objectPath = `${bucket}/${key}`
  const usePipe = size < 64 * 1024

  return new Promise((resolve, reject) => {
    const args = [
      'wrangler',
      'r2',
      'object',
      'put',
      objectPath,
      '--content-type',
      contentType,
      '--remote',
    ]
    if (usePipe) args.push('--pipe')
    else args.push('--file', file)

    const child = spawn('npx', args, {
      stdio: usePipe ? ['pipe', 'inherit', 'inherit'] : ['inherit', 'inherit', 'inherit'],
    })

    if (usePipe) {
      createReadStream(file).pipe(child.stdin)
    }

    child.on('error', reject)
    child.on('close', (code) => {
      if (code === 0) resolve()
      else reject(new Error(`upload failed for ${key} (exit ${code})`))
    })
  })
}

async function main() {
  const { tier, version, uploads } = JSON.parse(readFileSync(manifestPath, 'utf8'))
  const bucket = r2BucketForTier(tier)
  const totalBytes = uploads.reduce((sum, item) => sum + statSync(item.file).size, 0)

  console.log(`\nUploading ${tier} release ${version} to R2 bucket: ${bucket}`)
  console.log(`Files: ${uploads.length}  Total: ${fmtBytes(totalBytes)}\n`)

  const started = Date.now()
  for (let i = 0; i < uploads.length; i++) {
    const item = uploads[i]
    const size = statSync(item.file).size
    const itemStarted = Date.now()
    console.log(`[${i + 1}/${uploads.length}] ${item.key} (${fmtBytes(size)})`)
    await upload({ ...item, bucket })
    const elapsed = ((Date.now() - itemStarted) / 1000).toFixed(1)
    console.log(`  ✓ done in ${elapsed}s\n`)
  }

  console.log(`All uploads complete in ${((Date.now() - started) / 1000).toFixed(1)}s`)
  if (tierIsPublicCdn(tier)) {
    console.log(`\nVerify:`)
    console.log(`  curl -fsSL ${enterpriseManifestUrl(tier)} | head -20`)
  } else {
    console.log(`\nPrivate tier — objects are in ${bucket} (not on public CDN).`)
  }
}

main().catch((err) => {
  console.error('\n✗', err.message)
  process.exit(1)
})

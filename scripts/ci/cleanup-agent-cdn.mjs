#!/usr/bin/env node
/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

// SPDX-License-Identifier: Apache-2.0

/**
 * Prune stale enterprise agent objects from CDN (R2).
 *
 * CDN keeps a rolling `enterprise/<tier>/latest/` pointer only. On each publish,
 * filenames from the previous manifest that are not in the new manifest are deleted.
 * Immutable release history remains in GHCR (container images) and git tags.
 *
 * Usage:
 *   # Before upload — snapshot remote manifest for diff after publish
 *   node scripts/ci/cleanup-agent-cdn.mjs --tier tier-core --snapshot-remote
 *
 *   # After upload — delete stale objects (manifest diff)
 *   node scripts/ci/cleanup-agent-cdn.mjs --tier tier-core --prune-stale
 *
 *   # Optional orphan sweep — delete any object under latest/ not in remote manifest
 *   node scripts/ci/cleanup-agent-cdn.mjs --tier tier-core --sweep
 *
 *   node scripts/ci/cleanup-agent-cdn.mjs --all --prune-stale [--dry-run]
 *
 * Env: R2_BUCKET, R2_BUCKET_PRIVATE, CLOUDFLARE_ACCOUNT_ID, CLOUDFLARE_API_TOKEN (or wrangler login)
 */

import { existsSync, readFileSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import {
  ENTERPRISE_TIERS,
  enterpriseLatestPrefix,
  enterpriseManifestKey,
  enterpriseManifestUrl,
  R2_BUCKET_PUBLIC,
  r2BucketForTier,
  tierBySlug,
  tierIsPublicCdn,
} from './cdn-release-paths.mjs'
import {
  fetchJson,
  fetchR2Json,
  listR2ObjectKeys,
  wranglerDelete,
} from './r2-client.mjs'

const root = process.cwd()
const previousPath = join(root, 'release/cdn-previous-remote.json')
const uploadManifestPath = join(root, 'release/cdn-upload-manifest.json')

function parseArgs(argv) {
  const args = {
    tier: null,
    all: false,
    snapshotRemote: false,
    pruneStale: false,
    sweep: false,
    purgePublic: false,
    dryRun: false,
  }

  for (let i = 2; i < argv.length; i++) {
    const arg = argv[i]
    switch (arg) {
      case '--tier':
        args.tier = argv[++i]
        break
      case '--all':
        args.all = true
        break
      case '--snapshot-remote':
        args.snapshotRemote = true
        break
      case '--prune-stale':
        args.pruneStale = true
        break
      case '--sweep':
        args.sweep = true
        break
      case '--purge-public':
        args.purgePublic = true
        break
      case '--dry-run':
        args.dryRun = true
        break
      default:
        throw new Error(`Unknown argument: ${arg}`)
    }
  }

  if (!args.snapshotRemote && !args.pruneStale && !args.sweep && !args.purgePublic) {
    args.pruneStale = true
  }

  if (!args.all && !args.tier) {
    throw new Error('Provide --tier <slug> or --all')
  }

  return args
}

function manifestObjectKeys(manifest) {
  if (!manifest) return new Set()
  const keys = new Set([enterpriseManifestKey(manifest.tier)])
  for (const download of manifest.downloads || []) {
    keys.add(`${enterpriseLatestPrefix(manifest.tier)}/${download.filename}`)
  }
  for (const image of manifest.dockerImages || []) {
    keys.add(`${enterpriseLatestPrefix(manifest.tier)}/${image.filename}`)
  }
  if (manifest.helmChart?.filename) {
    keys.add(`${enterpriseLatestPrefix(manifest.tier)}/${manifest.helmChart.filename}`)
  }
  return keys
}

function keysFromRemoteManifest(manifest) {
  return manifestObjectKeys(manifest)
}

function keysFromUploadManifest(manifest) {
  return new Set(manifest.uploads.map((entry) => entry.key))
}

async function fetchRemoteManifest(tierSlug) {
  const bucket = r2BucketForTier(tierSlug)
  const key = enterpriseManifestKey(tierSlug)

  if (tierIsPublicCdn(tierSlug)) {
    return fetchJson(enterpriseManifestUrl(tierSlug))
  }

  return fetchR2Json(key, { bucket })
}

async function snapshotRemote(tierSlug, dryRun) {
  const bucket = r2BucketForTier(tierSlug)
  const key = enterpriseManifestKey(tierSlug)
  console.log(`Snapshot remote manifest: ${bucket}/${key}`)
  const manifest = await fetchRemoteManifest(tierSlug)
  if (!manifest) {
    console.log('  (no remote manifest — nothing to snapshot)')
    return
  }
  if (dryRun) {
    console.log(`  [dry-run] would write ${previousPath}`)
    return
  }
  writeFileSync(previousPath, `${JSON.stringify(manifest, null, 2)}\n`)
  console.log(`  wrote ${previousPath}`)
}

async function pruneStale(tierSlug, dryRun) {
  if (!existsSync(uploadManifestPath)) {
    throw new Error(`Missing ${uploadManifestPath} — run stage-agent-cdn.mjs first`)
  }

  const bucket = r2BucketForTier(tierSlug)
  const uploadManifest = JSON.parse(readFileSync(uploadManifestPath, 'utf8'))
  if (uploadManifest.tier !== tierSlug) {
    throw new Error(
      `Upload manifest tier ${uploadManifest.tier} does not match --tier ${tierSlug}`,
    )
  }

  const keepKeys = keysFromUploadManifest(uploadManifest)
  let previousKeys = new Set()

  if (existsSync(previousPath)) {
    const previous = JSON.parse(readFileSync(previousPath, 'utf8'))
    previousKeys = keysFromRemoteManifest(previous)
  } else {
    const remote = await fetchRemoteManifest(tierSlug)
    previousKeys = keysFromRemoteManifest(remote)
  }

  const staleKeys = [...previousKeys].filter((key) => !keepKeys.has(key))
  if (staleKeys.length === 0) {
    console.log(`No stale CDN objects for ${tierSlug} in ${bucket}`)
    return
  }

  console.log(`Pruning ${staleKeys.length} stale object(s) for ${tierSlug} from ${bucket}:`)
  for (const key of staleKeys) {
    console.log(`  - ${key}`)
    await wranglerDelete(key, { bucket, dryRun })
  }
}

async function sweepOrphans(tierSlug, dryRun) {
  const bucket = r2BucketForTier(tierSlug)
  const prefix = `${enterpriseLatestPrefix(tierSlug)}/`
  const remote = await fetchRemoteManifest(tierSlug)
  const keepKeys = manifestObjectKeys(remote)

  console.log(`Sweeping orphans under ${bucket}/${prefix}`)
  const listed = await listR2ObjectKeys(prefix, { bucket })
  const orphans = listed.filter((key) => !keepKeys.has(key))

  if (orphans.length === 0) {
    console.log('  no orphans found')
    return
  }

  console.log(`  deleting ${orphans.length} orphan(s):`)
  for (const key of orphans) {
    console.log(`  - ${key}`)
    await wranglerDelete(key, { bucket, dryRun })
  }
}

async function purgeFromPublicBucket(tierSlug, dryRun) {
  if (tierIsPublicCdn(tierSlug)) {
    throw new Error(`Refusing to purge public tier ${tierSlug} from public bucket`)
  }

  const prefix = `${enterpriseLatestPrefix(tierSlug)}/`
  console.log(`Purging ${tierSlug} from public bucket (legacy migration)`)

  let keys = []
  try {
    keys = await listR2ObjectKeys(prefix, { bucket: R2_BUCKET_PUBLIC })
  } catch (err) {
    console.log(`  R2 list unavailable (${err.message}) — falling back to public manifest keys`)
    const manifest = await fetchJson(enterpriseManifestUrl(tierSlug))
    keys = manifest ? [...manifestObjectKeys(manifest)] : []
  }

  if (keys.length === 0) {
    console.log(`  (no objects to delete under ${R2_BUCKET_PUBLIC}/${prefix})`)
    return
  }

  console.log(`Deleting ${keys.length} object(s) from ${R2_BUCKET_PUBLIC}/${prefix}`)
  for (const key of keys) {
    console.log(`  - ${key}`)
    await wranglerDelete(key, { bucket: R2_BUCKET_PUBLIC, dryRun })
  }
}

async function runForTier(tierSlug, args) {
  tierBySlug(tierSlug)
  console.log(`\n=== ${tierSlug} ===`)

  if (args.snapshotRemote) {
    await snapshotRemote(tierSlug, args.dryRun)
  }
  if (args.pruneStale) {
    await pruneStale(tierSlug, args.dryRun)
  }
  if (args.sweep) {
    await sweepOrphans(tierSlug, args.dryRun)
  }
  if (args.purgePublic) {
    await purgeFromPublicBucket(tierSlug, args.dryRun)
  }
}

async function main() {
  const args = parseArgs(process.argv)
  const tiers = args.all ? ENTERPRISE_TIERS.map((entry) => entry.slug) : [args.tier]

  for (const tierSlug of tiers) {
    await runForTier(tierSlug, args)
  }
}

main().catch((err) => {
  console.error(`\n✗ ${err.message}`)
  process.exit(1)
})

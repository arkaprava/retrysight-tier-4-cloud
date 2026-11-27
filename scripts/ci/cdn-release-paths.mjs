#!/usr/bin/env node
/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

// SPDX-License-Identifier: Apache-2.0

/**
 * Canonical release path and tag helpers for CDN (R2), GHCR, and git.
 *
 * Source of truth for path layout — docs/CDN_RELEASE_PATHS.md mirrors this file.
 */

import { readFileSync } from 'node:fs'
import { join } from 'node:path'

const root = process.cwd()

function loadEnvFile(path) {
  const values = {}
  try {
    for (const line of readFileSync(path, 'utf8').split('\n')) {
      const trimmed = line.trim()
      if (!trimmed || trimmed.startsWith('#')) continue
      const idx = trimmed.indexOf('=')
      if (idx === -1) continue
      values[trimmed.slice(0, idx).trim()] = trimmed.slice(idx + 1).trim()
    }
  } catch {
    // optional file
  }
  return values
}

const cdnEnv = loadEnvFile(join(root, '.github/cdn.env'))

export const CDN_BASE =
  process.env.RETRYSIGHT_CDN_BASE?.replace(/\/$/, '') ||
  cdnEnv.CDN_BASE ||
  'https://app.retrysight.com'

export const R2_BUCKET_PUBLIC =
  process.env.R2_BUCKET || cdnEnv.R2_BUCKET || 'download-retrysight'

export const R2_BUCKET_PRIVATE =
  process.env.R2_BUCKET_PRIVATE || cdnEnv.R2_BUCKET_PRIVATE || 'download-retrysite-private'

/** @deprecated Use r2BucketForTier() — kept for scripts that only touch tier-core. */
export const R2_BUCKET = R2_BUCKET_PUBLIC

/** Tier 1 Core is the only tier served from the public CDN bucket. */
export const PUBLIC_CDN_TIER_SLUGS = new Set(['tier-core', 'tier-analytics', 'tier-integration', 'tier-cloud'])

export function tierIsPublicCdn(tierSlug) {
  return PUBLIC_CDN_TIER_SLUGS.has(tierSlug)
}

export function r2BucketForTier(tierSlug) {
  return tierIsPublicCdn(tierSlug) ? R2_BUCKET_PUBLIC : R2_BUCKET_PRIVATE
}

export const GHCR_REGISTRY = cdnEnv.GHCR_REGISTRY || 'ghcr.io'
export const GHCR_OWNER = cdnEnv.GHCR_OWNER || 'arkaprava'

export const CLOUDFLARE_ACCOUNT_ID =
  process.env.CLOUDFLARE_ACCOUNT_ID || cdnEnv.CLOUDFLARE_ACCOUNT_ID || ''

/** Enterprise fleet agent tiers published under enterprise/<slug>/latest/ */
export const ENTERPRISE_TIERS = [
  {
    slug: 'tier-core',
    installationTier: 'TIER_1_CORE',
    repo: 'retrysight-tier-1-core',
    ghcrRepo: `${GHCR_OWNER}/retrysight-tier-1-core`,
  },
  {
    slug: 'tier-analytics',
    installationTier: 'TIER_2_ANALYTICS',
    repo: 'retrysight-tier-2-analytics',
    ghcrRepo: `${GHCR_OWNER}/retrysight-tier-2-analytics`,
  },
  {
    slug: 'tier-integration',
    installationTier: 'TIER_3_INTEGRATION',
    repo: 'retrysight-tier-3-integration',
    ghcrRepo: `${GHCR_OWNER}/retrysight-tier-3-integration`,
  },
  {
    slug: 'tier-cloud',
    installationTier: 'TIER_4_CLOUD',
    repo: 'retrysight-tier-4-cloud',
    ghcrRepo: `${GHCR_OWNER}/retrysight-tier-4-cloud`,
  },
]

export const GHCR_COMPONENTS = ['postgres', 'manager', 'frontend']

/** Git annotated tag pushed to trigger Release workflow + GHCR push. */
export function gitTag(version) {
  return `v${version}`
}

/** Rolling CDN prefix — only latest is kept; older filenames are pruned on publish. */
export function enterpriseLatestPrefix(tierSlug) {
  return `enterprise/${tierSlug}/latest`
}

export function enterpriseObjectKey(tierSlug, filename) {
  return `${enterpriseLatestPrefix(tierSlug)}/${filename}`
}

export function enterpriseManifestKey(tierSlug) {
  return `${enterpriseLatestPrefix(tierSlug)}/manifest.json`
}

export function enterpriseManifestUrl(tierSlug) {
  return `${CDN_BASE}/${enterpriseManifestKey(tierSlug)}`
}

/** Immutable container registry path (older tags remain in GHCR). */
export function ghcrImage(ghcrRepo, component, version) {
  return `${GHCR_REGISTRY}/${ghcrRepo}/${component}:${version}`
}

export function ghcrLatestImage(ghcrRepo, component) {
  return `${GHCR_REGISTRY}/${ghcrRepo}/${component}:latest`
}

/** Docker image tarball (docker save, tagged with the GHCR reference). */
export const DOCKER_ARCHES = ['amd64', 'arm64']

export function enterpriseDockerFilename(component, version, arch = 'amd64') {
  return `retrysight-${component}-${version}-linux-${arch}.tar.gz`
}

/** Immutable per-version prefix, published alongside latest/. */
export function enterpriseVersionPrefix(tierSlug, version) {
  return `enterprise/${tierSlug}/${version}`
}

export function enterpriseHelmFilename(version) {
  return `retrysight-${version}.tgz`
}

export function enterpriseComposeFilename() {
  return 'docker-compose.yml'
}

export function cdnDockerLoadCommand(tierSlug, component, version, arch = 'amd64') {
  const base = `${CDN_BASE}/${enterpriseLatestPrefix(tierSlug)}`
  const file = enterpriseDockerFilename(component, version, arch)
  return `curl -fsSL ${base}/${file} | docker load`
}

export function tierBySlug(slug) {
  const tier = ENTERPRISE_TIERS.find((entry) => entry.slug === slug)
  if (!tier) {
    throw new Error(`Unknown enterprise tier slug: ${slug}`)
  }
  return tier
}

export function readProductVersion() {
  try {
    return readFileSync(join(root, 'VERSION'), 'utf8').trim()
  } catch {
    return null
  }
}

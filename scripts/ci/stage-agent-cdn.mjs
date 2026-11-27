#!/usr/bin/env node
/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

// SPDX-License-Identifier: Apache-2.0

/**
 * Stage enterprise release artifacts for CDN upload (agents, Docker tars, Helm chart).
 *
 * Usage:
 *   node scripts/ci/stage-agent-cdn.mjs \
 *     --tier tier-core \
 *     --installationTier TIER_1_CORE \
 *     --version 1.0.0 \
 *     --source /path/to/agent-artifacts \
 *     [--docker-source release/docker-export] \
 *     [--helm-file release/retrysight-1.0.0.tgz] \
 *     [--compose-file release/docker-compose.yml]
 */

import { createHash } from 'node:crypto'
import {
  copyFileSync,
  createReadStream,
  existsSync,
  mkdirSync,
  readdirSync,
  statSync,
  writeFileSync,
} from 'node:fs'
import { spawnSync } from 'node:child_process'
import { basename, join } from 'node:path'
import {
  CDN_BASE,
  DOCKER_ARCHES,
  GHCR_COMPONENTS,
  ghcrImage,
  enterpriseComposeFilename,
  enterpriseDockerFilename,
  enterpriseHelmFilename,
  enterpriseLatestPrefix,
  enterpriseObjectKey,
  enterpriseVersionPrefix,
  tierBySlug,
} from './cdn-release-paths.mjs'

function parseArgs(argv) {
  const args = {}
  for (let i = 2; i < argv.length; i++) {
    const key = argv[i]
    const value = argv[i + 1]
    if (!key.startsWith('--') || value == null || value.startsWith('--')) {
      throw new Error(`Invalid argument: ${key}`)
    }
    // --docker-source → dockerSource
    const raw = key.slice(2)
    const camel = raw.replace(/-([a-z])/g, (_, c) => c.toUpperCase())
    args[camel] = value
    i++
  }
  for (const required of ['tier', 'installationTier', 'version', 'source']) {
    if (!args[required]) {
      throw new Error(`Missing required --${required}`)
    }
  }
  return args
}

async function sha256(filePath) {
  return new Promise((resolve, reject) => {
    const hash = createHash('sha256')
    createReadStream(filePath)
      .on('data', (chunk) => hash.update(chunk))
      .on('end', () => resolve(hash.digest('hex')))
      .on('error', reject)
  })
}

function findFiles(dir, pattern, { maxDepth = 6 } = {}) {
  if (!existsSync(dir)) return []
  const results = []
  const walk = (current, depth) => {
    let entries
    try {
      entries = readdirSync(current, { withFileTypes: true })
    } catch {
      return
    }
    for (const entry of entries) {
      const full = join(current, entry.name)
      if (entry.isDirectory()) {
        if (depth < maxDepth) walk(full, depth + 1)
      } else if (pattern.test(entry.name)) {
        results.push(full)
      }
    }
  }
  const st = statSync(dir)
  if (st.isFile()) {
    if (pattern.test(basename(dir))) results.push(dir)
    return results
  }
  walk(dir, 0)
  return results
}

function findAgentFiles(source, dirs, pattern) {
  const seen = new Set()
  const files = []
  for (const dir of dirs) {
    const root = dir ? join(source, dir) : source
    if (!existsSync(root)) continue
    for (const filePath of findFiles(root, pattern)) {
      if (seen.has(filePath)) continue
      seen.add(filePath)
      files.push(filePath)
    }
  }
  return files
}

function macosPkgDirs() {
  return ['agent-macos', 'agent-macos-arm64', 'agent-macos-x64']
}

function ensureComposeFile(explicitPath) {
  if (explicitPath && existsSync(explicitPath)) return explicitPath
  const defaultPath = join(process.cwd(), 'release', enterpriseComposeFilename())
  if (existsSync(defaultPath)) return defaultPath
  const script = join(process.cwd(), 'scripts/ci/render-cdn-compose.sh')
  if (existsSync(script)) {
    const result = spawnSync('bash', [script, defaultPath], { stdio: 'inherit' })
    if (result.status === 0 && existsSync(defaultPath)) return defaultPath
  }
  return null
}

function contentTypeFor(filename) {
  if (filename.endsWith('.json')) return 'application/json'
  if (filename.endsWith('.deb')) return 'application/vnd.debian.binary-package'
  if (filename.endsWith('.msi')) return 'application/x-msi'
  if (filename.endsWith('.pkg')) return 'application/octet-stream'
  if (filename.endsWith('.jar')) return 'application/java-archive'
  if (filename.endsWith('.tar.gz')) return 'application/gzip'
  if (filename.endsWith('.tgz')) return 'application/gzip'
  if (filename.endsWith('.yml') || filename.endsWith('.yaml')) return 'text/yaml'
  return 'application/octet-stream'
}

let releaseVersion = null

async function stageFile({ tier, filename, filePath, uploads, target, id, kind, extra = {} }) {
  const outDir = join(process.cwd(), 'release/cdn-staging/enterprise', tier, 'latest')
  mkdirSync(outDir, { recursive: true })

  const dest = join(outDir, filename)
  copyFileSync(filePath, dest)

  const size = statSync(dest).size
  const checksum = await sha256(dest)
  const key = enterpriseObjectKey(tier, filename)
  const url = `${CDN_BASE}/${key}`

  uploads.push({ key, file: dest, contentType: contentTypeFor(filename) })
  uploads.push({
    key: `${enterpriseVersionPrefix(tier, releaseVersion)}/${filename}`,
    file: dest,
    contentType: contentTypeFor(filename),
  })
  target.push({
    id,
    kind,
    filename,
    url,
    sha256: checksum,
    size,
    ...extra,
  })
}

async function main() {
  const { tier, installationTier, version, source, dockerSource, helmFile, composeFile } =
    parseArgs(process.argv)
  const tierMeta = tierBySlug(tier)
  releaseVersion = version

  const uploads = []
  const agentDownloads = []
  const dockerImages = []
  const seen = new Set()

  const macosPkgs = findAgentFiles(source, macosPkgDirs(), /\.pkg$/)
  const agentCandidates = [
    {
      id: 'linux-amd64',
      platform: 'linux',
      arch: 'amd64',
      kind: 'deb',
      files: findAgentFiles(source, ['agent-linux'], /\.deb$/),
    },
    {
      id: 'win-x64',
      platform: 'win32',
      arch: 'x64',
      kind: 'msi',
      files: findAgentFiles(source, ['agent-windows'], /\.msi$/),
    },
    {
      id: 'macos-arm64',
      platform: 'darwin',
      arch: 'arm64',
      kind: 'pkg',
      files: macosPkgs.filter((path) => /arm64/.test(basename(path))),
    },
    {
      id: 'macos-x64',
      platform: 'darwin',
      arch: 'x64',
      kind: 'pkg',
      files: macosPkgs.filter((path) => /(x64|amd64)/.test(basename(path))),
    },
    {
      id: 'portable-jar',
      platform: 'any',
      arch: 'any',
      kind: 'jar',
      files: findAgentFiles(
        source,
        ['agent-linux', 'agent-windows', ...macosPkgDirs()],
        /\.jar$/,
      ),
    },
  ]

  for (const entry of agentCandidates) {
    const filePath = entry.files[0]
    if (!filePath || seen.has(filePath)) continue
    seen.add(filePath)

    await stageFile({
      tier,
      filename: basename(filePath),
      filePath,
      uploads,
      target: agentDownloads,
      id: entry.id,
      kind: entry.kind,
      extra: { platform: entry.platform, arch: entry.arch },
    })
  }

  if (agentDownloads.length === 0) {
    throw new Error(`No agent artifacts found under ${source}`)
  }

  const dockerDir = dockerSource || join(process.cwd(), 'release/docker-export')
  if (existsSync(dockerDir)) {
    for (const component of GHCR_COMPONENTS) {
      for (const arch of DOCKER_ARCHES) {
        const expected = enterpriseDockerFilename(component, version, arch)
        const filePath = findFiles(dockerDir, /\.tar\.gz$/).find((path) => basename(path) === expected)
        if (!filePath) continue

        await stageFile({
          tier,
          filename: expected,
          filePath,
          uploads,
          target: dockerImages,
          id: `${component}-linux-${arch}`,
          kind: 'docker-image',
          extra: {
            component,
            platform: 'linux',
            arch,
            image: ghcrImage(tierMeta.ghcrRepo, component, version),
          },
        })
      }
    }
  }

  let helmChart = null
  const helmPath =
    helmFile ||
    findFiles(join(process.cwd(), 'release'), /^retrysight-.*\.tgz$/, { maxDepth: 0 }).find((path) =>
      basename(path).includes(version),
    )
  if (helmPath && existsSync(helmPath)) {
    const filename = enterpriseHelmFilename(version)
    const staged = []
    await stageFile({
      tier,
      filename,
      filePath: helmPath,
      uploads,
      target: staged,
      id: 'helm-chart',
      kind: 'helm-chart',
    })
    helmChart = staged[0]
  }

  let compose = null
  const composePath = ensureComposeFile(composeFile)
  if (composePath && existsSync(composePath)) {
    const filename = enterpriseComposeFilename()
    const staged = []
    await stageFile({
      tier,
      filename,
      filePath: composePath,
      uploads,
      target: staged,
      id: 'compose',
      kind: 'compose',
    })
    compose = staged[0]
  }

  const manifest = {
    schema: 3,
    product: 'RetrySight',
    license: 'Apache-2.0',
    tier,
    installationTier,
    version,
    releasedAt: new Date().toISOString(),
    baseUrl: `${CDN_BASE}/${enterpriseLatestPrefix(tier)}`,
    versionBaseUrl: `${CDN_BASE}/${enterpriseVersionPrefix(tier, version)}`,
    githubRepo: `https://github.com/${tierMeta.ghcrRepo}`,
    ghcrRepo: `${tierMeta.ghcrRepo}`,
    downloads: agentDownloads,
    dockerImages,
    ...(helmChart ? { helmChart } : {}),
    ...(compose ? { composeFile: compose } : {}),
  }

  const outDir = join(process.cwd(), 'release/cdn-staging/enterprise', tier, 'latest')
  const manifestPath = join(outDir, 'manifest.json')
  writeFileSync(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`)
  for (const prefix of [enterpriseLatestPrefix(tier), enterpriseVersionPrefix(tier, version)]) {
    uploads.push({ key: `${prefix}/manifest.json`, file: manifestPath, contentType: 'application/json' })
  }

  const manifestSidecar = join(process.cwd(), 'release/cdn-upload-manifest.json')
  writeFileSync(
    manifestSidecar,
    `${JSON.stringify({ tier, version, uploads }, null, 2)}\n`,
  )

  console.log(
    `Staged ${agentDownloads.length} agent, ${dockerImages.length} docker, ${helmChart ? 1 : 0} helm, ${compose ? 1 : 0} compose for ${tier}`,
  )
  for (const item of uploads) {
    console.log(`  ${item.key}`)
  }
}

main().catch((err) => {
  console.error(err.message)
  process.exit(1)
})

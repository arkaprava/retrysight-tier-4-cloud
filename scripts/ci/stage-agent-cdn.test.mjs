#!/usr/bin/env node
/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

// SPDX-License-Identifier: Apache-2.0
/**
 * Dry-run scripts/ci/stage-agent-cdn.mjs with fake agent + compose files.
 * Usage: node scripts/ci/stage-agent-cdn.test.mjs
 */

import { mkdtempSync, mkdirSync, writeFileSync, readFileSync, rmSync, existsSync } from 'node:fs'
import { join } from 'node:path'
import { tmpdir } from 'node:os'
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'

const root = join(fileURLToPath(new URL('.', import.meta.url)), '../..')
const tierEnv = Object.fromEntries(
  readFileSync(join(root, '.github/tier.env'), 'utf8')
    .split('\n')
    .filter((l) => l.includes('=') && !l.trim().startsWith('#'))
    .map((l) => [l.slice(0, l.indexOf('=')).trim(), l.slice(l.indexOf('=') + 1).trim().replace(/^"|"$/g, '')]),
)
const tierSlug = tierEnv.TIER_SLUG

function assert(cond, msg) {
  if (!cond) {
    console.error(`FAIL: ${msg}`)
    process.exit(1)
  }
}

const source = mkdtempSync(join(tmpdir(), 'retrysight-stage-cdn-'))
const dirs = {
  linux: join(source, 'agent-linux'),
  win: join(source, 'agent-windows'),
  macArm: join(source, 'agent-macos-arm64'),
  macX64: join(source, 'agent-macos-x64'),
}
for (const dir of Object.values(dirs)) mkdirSync(dir, { recursive: true })

writeFileSync(join(dirs.linux, 'retrysight-agent_1.0.0_amd64.deb'), 'deb\n')
writeFileSync(join(dirs.win, 'RetrySightAgent-1.0.0.msi'), 'msi\n')
writeFileSync(join(dirs.macArm, 'RetrySightAgent-1.0.0-arm64.pkg'), 'pkg-arm64\n')
writeFileSync(join(dirs.macX64, 'RetrySightAgent-1.0.0-x64.pkg'), 'pkg-x64\n')
const version = readFileSync(join(root, 'VERSION'), 'utf8').trim()
writeFileSync(join(dirs.linux, `retrysight-agent-${version}.jar`), 'jar\n')

const dockerDir = join(source, 'docker-export')
mkdirSync(dockerDir, { recursive: true })
for (const component of ['postgres', 'manager', 'frontend']) {
  for (const arch of ['amd64', 'arm64']) {
    writeFileSync(join(dockerDir, `retrysight-${component}-${version}-linux-${arch}.tar.gz`), `${component}-${arch}\n`)
  }
}

const composePath = join(root, 'release', 'docker-compose.yml')
if (!existsSync(composePath)) {
  const rendered = spawnSync('bash', [join(root, 'scripts/ci/render-cdn-compose.sh'), composePath], {
    cwd: root,
    stdio: 'inherit',
  })
  assert(rendered.status === 0, 'render-cdn-compose.sh failed')
}

const result = spawnSync(
  process.execPath,
  [
    join(root, 'scripts/ci/stage-agent-cdn.mjs'),
    '--tier',
    tierSlug,
    '--installationTier',
    tierEnv.INSTALLATION_TIER,
    '--version',
    version,
    '--source',
    source,
    '--compose-file',
    composePath,
    '--docker-source',
    dockerDir,
  ],
  { cwd: root, encoding: 'utf8' },
)

if (result.status !== 0) {
  console.error(result.stdout || '')
  console.error(result.stderr || '')
  assert(false, `stage-agent-cdn.mjs exited ${result.status}`)
}

const manifestPath = join(root, `release/cdn-staging/enterprise/${tierSlug}/latest/manifest.json`)
assert(existsSync(manifestPath), `missing ${manifestPath}`)
const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'))

const ids = (manifest.downloads || []).map((d) => d.id)
assert(ids.includes('linux-amd64'), 'missing linux-amd64')
assert(ids.includes('win-x64'), 'missing win-x64')
assert(ids.includes('macos-arm64'), 'missing macos-arm64')
assert(ids.includes('macos-x64'), 'missing macos-x64')
assert(ids.includes('portable-jar'), 'missing portable-jar')
assert(!ids.includes('macos'), 'legacy id macos should be gone')
assert(
  !(manifest.downloads || []).some((d) => d.arch === 'universal'),
  'must not hardcode arch: universal',
)

const arm = manifest.downloads.find((d) => d.id === 'macos-arm64')
const intel = manifest.downloads.find((d) => d.id === 'macos-x64')
assert(arm?.arch === 'arm64' && /arm64/.test(arm.filename), `bad macos-arm64 entry: ${JSON.stringify(arm)}`)
assert(intel?.arch === 'x64' && /x64/.test(intel.filename), `bad macos-x64 entry: ${JSON.stringify(intel)}`)

assert(manifest.composeFile, 'missing composeFile object')
assert(manifest.composeFile.kind === 'compose', 'composeFile.kind should be compose')
assert(manifest.composeFile.filename === 'docker-compose.yml', 'composeFile.filename')
assert(manifest.composeFile.id === 'compose', 'composeFile.id')
assert(typeof manifest.composeFile.sha256 === 'string' && manifest.composeFile.sha256.length === 64, 'compose sha256')
assert(typeof manifest.composeFile.size === 'number' && manifest.composeFile.size > 0, 'compose size')
assert(
  existsSync(join(root, `release/cdn-staging/enterprise/${tierSlug}/latest/docker-compose.yml`)),
  'docker-compose.yml not staged',
)

const images = manifest.dockerImages || []
assert(images.length === 6, `expected 6 docker tarballs (3 components x 2 arches), got ${images.length}`)
for (const img of images) {
  assert(/^ghcr\.io\/arkaprava\/retrysight-tier-[1-4]-[a-z]+\/(postgres|manager|frontend):/.test(img.image), `bad image ref ${img.image}`)
  assert(img.filename === `retrysight-${img.component}-${version}-linux-${img.arch}.tar.gz`, `bad tarball name ${img.filename}`)
}
assert(manifest.schema === 3 && manifest.license === 'Apache-2.0', 'manifest schema/license')
assert(manifest.versionBaseUrl.endsWith(`/enterprise/${tierSlug}/${version}`), 'versionBaseUrl')

const plan = JSON.parse(readFileSync(join(root, 'release/cdn-upload-manifest.json'), 'utf8'))
const keys = new Set(plan.uploads.map((u) => u.key))
for (const prefix of [`enterprise/${tierSlug}/latest`, `enterprise/${tierSlug}/${version}`]) {
  assert(keys.has(`${prefix}/manifest.json`), `missing ${prefix}/manifest.json`)
  assert(keys.has(`${prefix}/docker-compose.yml`), `missing ${prefix}/docker-compose.yml`)
  assert(keys.has(`${prefix}/retrysight-manager-${version}-linux-arm64.tar.gz`), `missing ${prefix} arm64 manager`)
}

rmSync(source, { recursive: true, force: true })
console.log(`OK: stage-agent-cdn.mjs dry-run for ${tierSlug} (agents, multi-arch images, compose, latest + versioned keys)`)

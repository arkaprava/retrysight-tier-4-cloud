<!--
  Copyright (c) 2026 Arka Prava Ghosh
  SPDX-License-Identifier: Apache-2.0
-->
# CDN release paths

All four tiers publish to the public R2 bucket `download-retrysight`, served at
`https://app.retrysight.com`. Paths are built by [`scripts/ci/cdn-release-paths.mjs`](../scripts/ci/cdn-release-paths.mjs).

| Prefix | Contents |
|--------|----------|
| `enterprise/tier-cloud/<version>/` | Immutable copy of one release |
| `enterprise/tier-cloud/latest/` | The newest release; stale files are pruned on publish |

Each prefix holds:

| File | Notes |
|------|-------|
| `manifest.json` | Schema 3: version, `baseUrl`, `versionBaseUrl`, `githubRepo`, `downloads[]` (agents), `dockerImages[]` (component, arch, GHCR `image`, sha256, size), `helmChart`, `composeFile` |
| `docker-compose.yml` | Pull-only stack using `ghcr.io/arkaprava/retrysight-tier-4-cloud/<component>:<version>` |
| `retrysight-<version>.tgz` | Helm chart (defaults to the GHCR images) |
| `retrysight-<component>-<version>-linux-<arch>.tar.gz` | `docker save` of each image (`postgres`, `manager`, `frontend` × `amd64`, `arm64`), tagged with the GHCR reference |
| `retrysight-agent_<v>_amd64.deb`, `RetrySightAgent-<v>.msi`, `RetrySightAgent-<v>-{arm64,x64}.pkg`, `retrysight-agent-<version>.jar` | Collector agent |

```bash
BASE=https://app.retrysight.com/enterprise/tier-cloud/latest
curl -fsSL "$BASE/manifest.json" | jq '.version, .dockerImages[].filename'
curl -fsSL "$BASE/retrysight-manager-1.0.0-linux-amd64.tar.gz" | docker load
```

Images are also on GHCR: `ghcr.io/arkaprava/retrysight-tier-4-cloud/{postgres,manager,frontend}:<version>` (multi-arch) and `:latest`.

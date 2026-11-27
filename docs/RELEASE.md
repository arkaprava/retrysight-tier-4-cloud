<!--
  Copyright (c) 2026 Arka Prava Ghosh
  SPDX-License-Identifier: Apache-2.0
-->
# Releasing

Releases are cut by pushing a `v<VERSION>` tag. [`.github/workflows/release.yml`](../.github/workflows/release.yml)
then tests, builds and publishes everything; nothing is uploaded from a laptop.

## Cut a release

```bash
echo 1.0.1 > VERSION                       # the tag must match VERSION or the workflow fails
sed -i '' 's/^  tag: .*/  tag: "1.0.1"/' helm/retrysight/values.yaml   # package-helm.sh syncs Chart.yaml
git commit -am "Release 1.0.1" && git tag v1.0.1 && git push origin main v1.0.1
```

Versions with a suffix (`1.1.0-rc.1`) become GitHub pre-releases and do not move the `latest` image tag.

## What the workflow does

| Job | Runs on | Output |
|-----|---------|--------|
| Test | every push / PR | `mvn verify`, dashboard build, Helm lint, license headers, CDN staging dry run |
| Docker images | every push / PR (build check); tags: publish | `ghcr.io/arkaprava/retrysight-tier-4-cloud/{postgres,manager,frontend}:<version>` and `:latest` for `linux/amd64` + `linux/arm64`; per-arch `docker save` tarballs |
| Helm chart + compose | every push / PR | `retrysight-<version>.tgz`, `docker-compose.yml` (pull-only, GHCR images), `manifest.yaml` |
| Agent | every push / PR | Linux `.deb`, Windows `.msi`, macOS `.pkg` (arm64 + x64), portable jar |
| Publish | tags only | CDN upload to `https://app.retrysight.com/enterprise/tier-cloud/<version>/` and `/latest/`, `manifest.json`, GitHub Release with all files |

## One-time setup (repository settings)

| Setting | Where | Value |
|---------|-------|-------|
| `CLOUDFLARE_API_TOKEN` | Secrets → Actions | Token with **R2 edit** on the `download-retrysight` bucket |
| `CLOUDFLARE_ACCOUNT_ID` | Variables (or Secrets) → Actions | Cloudflare account that owns the bucket |
| `release` environment | Settings → Environments | Optional: add required reviewers to gate CDN publishing |
| GHCR package visibility | Package settings | **Public** for `postgres`, `manager`, `frontend` after the first release |

## Building locally

```bash
mvn -B -q package -Dmaven.test.skip=true -pl manager/manager-app -am
bash scripts/ci/build-images.sh                      # build check (linux/amd64)
EXPORT_IMAGES=true bash scripts/ci/build-images.sh   # + release/docker-export/*.tar.gz for both arches
bash scripts/ci/package-helm.sh                      # release/retrysight-<version>.tgz
bash scripts/ci/render-cdn-compose.sh                # release/docker-compose.yml
node scripts/ci/stage-agent-cdn.test.mjs             # CDN layout dry run
```

`scripts/ci/publish-agent-cdn.sh` can still upload a release by hand (it needs the same Cloudflare
credentials); prefer the workflow so every release is reproducible.

## Installing a release

| Method | Command |
|--------|---------|
| Compose | `curl -fsSL https://app.retrysight.com/enterprise/tier-cloud/latest/docker-compose.yml -o docker-compose.yml && docker compose up -d` |
| Helm | `helm upgrade --install retrysight https://app.retrysight.com/enterprise/tier-cloud/latest/retrysight-<version>.tgz -n retrysight --create-namespace` |
| Images | `docker pull ghcr.io/arkaprava/retrysight-tier-4-cloud/manager:<version>` |
| Air-gapped | `docker load -i retrysight-<component>-<version>-linux-<amd64\|arm64>.tar.gz`, then use the compose file or chart unchanged |

Dashboard: http://localhost:8086 · Manager API: http://localhost:28477. CDN layout: [CDN_RELEASE_PATHS.md](CDN_RELEASE_PATHS.md).

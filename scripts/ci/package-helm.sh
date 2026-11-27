#!/usr/bin/env bash
# Copyright (c) 2026 Arka Prava Ghosh
# SPDX-License-Identifier: Apache-2.0

# Package the Helm chart and write a release manifest.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT}"

# shellcheck source=/dev/null
source "${ROOT}/.github/tier.env"

VERSION="$(tr -d '[:space:]' < "${ROOT}/VERSION" 2>/dev/null || echo 0.0.0-dev)"
# jpackage --app-version is numeric semver; pre-1.0 product versions become 1.0.0.
JPACKAGE_VERSION="$(printf '%s' "${VERSION}" | sed -E 's/^([0-9]+\.[0-9]+\.[0-9]+).*/\1/')"
if [[ ! "${JPACKAGE_VERSION}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || [[ "${JPACKAGE_VERSION}" =~ ^0\. ]]; then
  JPACKAGE_VERSION="1.0.0"
fi
CHART_DIR="${ROOT}/helm/retrysight"
OUTPUT_DIR="${ROOT}/release"
REGISTRY="${REGISTRY:-ghcr.io}"
IMAGE_REPO="${IMAGE_REPO:-${GITHUB_REPOSITORY:-local/retrysight}}"

mkdir -p "${OUTPUT_DIR}"

# Sync chart version with product VERSION
sed -i.bak "s/^version:.*/version: ${VERSION}/" "${CHART_DIR}/Chart.yaml"
sed -i.bak "s/^appVersion:.*/appVersion: \"${VERSION}\"/" "${CHART_DIR}/Chart.yaml"
# Keep packaged values aligned with the release tag (avoids stale defaults like 0.1.0-rc.1).
sed -i.bak -E "s/^(  tag: ).*/\\1\"${VERSION}\"/" "${CHART_DIR}/values.yaml"
rm -f "${CHART_DIR}/Chart.yaml.bak" "${CHART_DIR}/values.yaml.bak"

helm lint "${CHART_DIR}" \
  --set "images.tag=${VERSION}" \
  --set "tier.slug=${TIER_SLUG}" \
  --set "tier.installationTier=${INSTALLATION_TIER}" \
  --set-literal "tier.springProfiles=${SPRING_PROFILES}" \
  --set "postgres.database=${POSTGRES_DB}" \
  --set "postgres.user=${POSTGRES_USER}" \
  --set "manager.port=${MANAGER_PORT}" \
  --set "frontend.port=${FRONTEND_PORT}" \
  --set "manager.clickhouseEnabled=${CLICKHOUSE_ENABLED}" \
  --set "clickhouse.enabled=${CLICKHOUSE_ENABLED}" \
  --set "minio.enabled=${MINIO_ENABLED}"

helm package "${CHART_DIR}" \
  --version "${VERSION}" \
  --app-version "${VERSION}" \
  --destination "${OUTPUT_DIR}"

cat > "${OUTPUT_DIR}/manifest.yaml" <<EOF
version: "${VERSION}"
tier: ${INSTALLATION_TIER}
spring_profiles: ${SPRING_PROFILES}
images:
  registry: ${REGISTRY}
  repository: ${IMAGE_REPO}
  tag: ${VERSION}
  postgres: ${REGISTRY}/${IMAGE_REPO}/postgres:${VERSION}
  manager: ${REGISTRY}/${IMAGE_REPO}/manager:${VERSION}
  frontend: ${REGISTRY}/${IMAGE_REPO}/frontend:${VERSION}
helm_chart: retrysight-${VERSION}.tgz
agent_installers:
  linux: retrysight-agent_${JPACKAGE_VERSION}_amd64.deb
  windows: RetrySightAgent-${JPACKAGE_VERSION}.msi
  macos_arm64: RetrySightAgent-${JPACKAGE_VERSION}-arm64.pkg
  macos_x64: RetrySightAgent-${JPACKAGE_VERSION}-x64.pkg
EOF

echo "Helm chart and manifest written to ${OUTPUT_DIR}/"

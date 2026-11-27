#!/usr/bin/env bash
# Copyright (c) 2026 Arka Prava Ghosh
# SPDX-License-Identifier: Apache-2.0

# Stage and upload enterprise release artifacts (agents, Docker tars, compose, Helm) to CDN.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT}"

# shellcheck source=/dev/null
source "${ROOT}/.github/tier.env"

SOURCE_DIR="${1:-}"
VERSION="$(tr -d '[:space:]' < "${ROOT}/VERSION")"
SKIP_CLEANUP="${SKIP_CDN_CLEANUP:-false}"
DOCKER_SOURCE="${DOCKER_SOURCE:-${ROOT}/release/docker-export}"
HELM_FILE="${HELM_FILE:-${ROOT}/release/retrysight-${VERSION}.tgz}"
COMPOSE_FILE="${COMPOSE_FILE:-}"

if [[ -z "${SOURCE_DIR}" ]]; then
  echo "Usage: $0 /path/to/downloaded-agent-artifacts" >&2
  exit 1
fi

if [[ ! -d "${SOURCE_DIR}" ]]; then
  echo "Source directory not found: ${SOURCE_DIR}" >&2
  exit 1
fi

if [[ "${SKIP_CLEANUP}" != "true" ]]; then
  node "${ROOT}/scripts/ci/cleanup-agent-cdn.mjs" --tier "${TIER_SLUG}" --snapshot-remote
fi

STAGE_ARGS=(
  --tier "${TIER_SLUG}"
  --installationTier "${INSTALLATION_TIER}"
  --version "${VERSION}"
  --source "${SOURCE_DIR}"
)

if [[ -d "${DOCKER_SOURCE}" ]]; then
  STAGE_ARGS+=(--docker-source "${DOCKER_SOURCE}")
fi

if [[ -f "${HELM_FILE}" ]]; then
  STAGE_ARGS+=(--helm-file "${HELM_FILE}")
fi

if [[ -z "${COMPOSE_FILE}" ]]; then
  bash "${ROOT}/scripts/ci/render-cdn-compose.sh" "${ROOT}/release/docker-compose.yml"
  COMPOSE_FILE="${ROOT}/release/docker-compose.yml"
elif [[ ! -f "${COMPOSE_FILE}" ]]; then
  echo "Compose file not found: ${COMPOSE_FILE}" >&2
  exit 1
fi
STAGE_ARGS+=(--compose-file "${COMPOSE_FILE}")

node "${ROOT}/scripts/ci/stage-agent-cdn.mjs" "${STAGE_ARGS[@]}"
node "${ROOT}/scripts/ci/upload-agent-r2.mjs"

if [[ "${SKIP_CLEANUP}" != "true" ]]; then
  node "${ROOT}/scripts/ci/cleanup-agent-cdn.mjs" --tier "${TIER_SLUG}" --prune-stale
fi

#!/usr/bin/env bash
# Copyright (c) 2026 Arka Prava Ghosh
# SPDX-License-Identifier: Apache-2.0

# Render release/docker-compose.yml (pull-only, public GHCR images) from docker-compose.yml.
# Usage: render-cdn-compose.sh [out-file]
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
VERSION="${RETRYSIGHT_VERSION:-$(tr -d '[:space:]' < "${ROOT}/VERSION")}"
IMAGE_REPO="${IMAGE_REPO:-${GITHUB_REPOSITORY:-arkaprava/$(basename "${ROOT}")}}"
OUT="${1:-${ROOT}/release/docker-compose.yml}"

python3 "${ROOT}/scripts/ci/render-cdn-compose.py" "${VERSION}" "${IMAGE_REPO}" "${OUT}"
if command -v docker >/dev/null 2>&1; then
  docker compose -f "${OUT}" config -q
fi

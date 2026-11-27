#!/usr/bin/env bash
# Copyright (c) 2026 Arka Prava Ghosh
# SPDX-License-Identifier: Apache-2.0

# Prune stale enterprise agent objects from CDN for one or all tiers.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT}"

DRY_RUN=""
MODE="--prune-stale"
TIER=""
ALL=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run) DRY_RUN="--dry-run" ;;
    --sweep) MODE="--sweep" ;;
    --snapshot-remote) MODE="--snapshot-remote" ;;
    --all) ALL="--all" ;;
    --tier)
      shift
      TIER="--tier $1"
      ;;
    *)
      echo "Usage: $0 [--all | --tier <slug>] [--prune-stale | --sweep | --snapshot-remote] [--dry-run]" >&2
      exit 1
      ;;
  esac
  shift
done

if [[ -z "${ALL}" && -z "${TIER}" ]]; then
  # shellcheck source=/dev/null
  source "${ROOT}/.github/tier.env"
  TIER="--tier ${TIER_SLUG}"
fi

# shellcheck disable=SC2086
node "${ROOT}/scripts/ci/cleanup-agent-cdn.mjs" ${TIER} ${ALL} ${MODE} ${DRY_RUN}

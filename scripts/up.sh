#!/usr/bin/env bash
# Copyright (c) 2026 Arka Prava Ghosh
# SPDX-License-Identifier: Apache-2.0

set -euo pipefail
TIER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${TIER_DIR}"

ENV_FILE=".env"
[[ -f "$ENV_FILE" ]] || ENV_FILE=".env.example"
docker compose --env-file "$ENV_FILE" -f docker-compose.yml up -d "$@"
echo "Stack started (see README.md for URLs)."

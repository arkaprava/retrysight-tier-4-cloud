#!/usr/bin/env bash
# Copyright (c) 2026 Arka Prava Ghosh
# SPDX-License-Identifier: Apache-2.0

set -euo pipefail
TIER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${TIER_DIR}"

if [[ -x scripts/build-product.sh ]]; then
  ./scripts/build-product.sh "$@"
else
  mvn -pl manager/manager-app,agent/agent-app -am package -DskipTests -B -q
  (cd ui/manager-dashboard && npm ci --silent && npm run build --silent)
  docker compose --env-file .env.example -f docker-compose.yml build "$@"
fi
echo "Product images built."

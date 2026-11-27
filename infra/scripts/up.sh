#!/usr/bin/env bash
# Copyright (c) 2026 Arka Prava Ghosh
# SPDX-License-Identifier: Apache-2.0

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_FILE="${ROOT_DIR}/infra/docker-compose.yml"
ENV_FILE="${ROOT_DIR}/infra/.env"

COMPOSE_ARGS=()
UP_ARGS=("$@")
if [[ "${INCLUDE_AGENT:-}" == "1" ]]; then
  COMPOSE_ARGS+=(--profile agent)
fi

cd "${ROOT_DIR}"

compose_cmd=(docker compose -f "${COMPOSE_FILE}")
if [[ -f "${ENV_FILE}" ]]; then
  compose_cmd=(docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}")
fi
if ((${#COMPOSE_ARGS[@]})); then
  compose_cmd+=("${COMPOSE_ARGS[@]}")
fi

if ((${#UP_ARGS[@]})); then
  "${compose_cmd[@]}" up -d "${UP_ARGS[@]}"
else
  "${compose_cmd[@]}" up -d
fi

echo "Stack started."
echo "  Dashboard: http://localhost:${FRONTEND_PORT:-8080}"
echo "  Manager:   http://localhost:${MANAGER_PORT:-28477}"

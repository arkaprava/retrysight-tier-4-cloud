#!/usr/bin/env bash
# Copyright (c) 2026 Arka Prava Ghosh
# SPDX-License-Identifier: Apache-2.0

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

cd "${ROOT_DIR}"

mvn -pl manager/manager-app -am package -DskipTests -B -q

docker build -f infra/manager/Dockerfile -t agent-retry/manager:local .

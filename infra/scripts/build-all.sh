#!/usr/bin/env bash
# Copyright (c) 2026 Arka Prava Ghosh
# SPDX-License-Identifier: Apache-2.0

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

"${SCRIPT_DIR}/build-postgres.sh"
"${SCRIPT_DIR}/build-manager.sh"
"${SCRIPT_DIR}/build-frontend.sh"
"${SCRIPT_DIR}/build-agent.sh"

echo "All images built."

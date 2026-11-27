#!/usr/bin/env bash
# Copyright (c) 2026 Arka Prava Ghosh
# SPDX-License-Identifier: Apache-2.0

set -euo pipefail

# Start postgres + manager + frontend + agent collector
export INCLUDE_AGENT=1
exec "$(dirname "$0")/up.sh" --build "$@"

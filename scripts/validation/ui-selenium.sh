#!/usr/bin/env bash
# Copyright (c) 2026 Arka Prava Ghosh
# SPDX-License-Identifier: Apache-2.0

# Run Selenium UI smoke tests (requires manager + UI + Chrome/Chromium).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
source "${SCRIPT_DIR}/lib.sh"
SELENIUM_DIR="${SCRIPT_DIR}/selenium"
VENV_DIR="${SELENIUM_DIR}/.venv"

require_cmd python3
require_cmd curl

UI_CODE="$(http_code GET "${UI_URL}/" || true)"
MGR_CODE="$(http_code GET "${MANAGER_URL}/graphiql" || true)"
if [[ "${UI_CODE}" != "200" && "${UI_CODE}" != "304" ]]; then
  echo "UI not reachable at ${UI_URL} (HTTP ${UI_CODE:-none}). Start the dashboard first." >&2
  exit 1
fi
if [[ "${MGR_CODE}" == "000" || -z "${MGR_CODE}" ]]; then
  echo "Manager not reachable at ${MANAGER_URL}. Start the manager first." >&2
  exit 1
fi

if [[ ! -d "${VENV_DIR}" ]]; then
  echo "Creating venv at ${VENV_DIR}"
  python3 -m venv "${VENV_DIR}"
fi
# shellcheck disable=SC1091
source "${VENV_DIR}/bin/activate"
pip install -q -r "${SELENIUM_DIR}/requirements.txt"

export UI_URL APM_EMAIL APM_PASSWORD
export SELENIUM_HEADLESS="${SELENIUM_HEADLESS:-1}"

exec python3 "${SELENIUM_DIR}/ui_smoke.py"

#!/usr/bin/env bash
# Copyright (c) 2026 Arka Prava Ghosh
# SPDX-License-Identifier: Apache-2.0

# Install / refresh OS auto-start for a jar-based or jpackage agent install.

set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OS="$(uname -s)"
INSTALL_DIR="${RETRYSIGHT_AGENT_HOME:-${HOME}/.retrysight-agent}"

# shellcheck source=/dev/null
if [[ -f "${ROOT_DIR}/.github/tier.env" ]]; then
  source "${ROOT_DIR}/.github/tier.env"
fi
AGENT_PROFILE="${TIER_SLUG:-tier-core}"

resolve_macos_launcher() {
  local candidate
  for candidate in \
    "/Applications/RetrySightAgent.app/Contents/MacOS/RetrySightAgent" \
    "${INSTALL_DIR}/RetrySightAgent.app/Contents/MacOS/RetrySightAgent"
  do
    if [[ -x "${candidate}" ]]; then
      echo "${candidate}"
      return 0
    fi
  done
  return 1
}

resolve_linux_launcher() {
  local candidate
  for candidate in \
    "/opt/retrysight-agent/bin/RetrySightAgent" \
    "/opt/RetrySightAgent/bin/RetrySightAgent" \
    "${INSTALL_DIR}/bin/RetrySightAgent"
  do
    if [[ -x "${candidate}" ]]; then
      echo "${candidate}"
      return 0
    fi
  done
  return 1
}

if [[ "${OS}" == "Darwin" ]]; then
  TARGET="${HOME}/Library/LaunchAgents/com.retrysight.agent.plist"
  mkdir -p "$(dirname "${TARGET}")"
  if LAUNCHER="$(resolve_macos_launcher)"; then
    cat > "${TARGET}" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key>
  <string>com.retrysight.agent</string>
  <key>ProgramArguments</key>
  <array>
    <string>${LAUNCHER}</string>
  </array>
  <key>RunAtLoad</key>
  <true/>
  <key>KeepAlive</key>
  <true/>
  <key>ThrottleInterval</key>
  <integer>10</integer>
  <key>StandardOutPath</key>
  <string>/tmp/retrysight-agent.log</string>
  <key>StandardErrorPath</key>
  <string>/tmp/retrysight-agent.err</string>
  <key>EnvironmentVariables</key>
  <dict>
    <key>SPRING_PROFILES_ACTIVE</key>
    <string>${AGENT_PROFILE}</string>
  </dict>
</dict>
</plist>
EOF
  else
    if [[ ! -f "${INSTALL_DIR}/agent-app.jar" ]]; then
      echo "Packaging agent jar first..."
      "${ROOT_DIR}/scripts/package-agent.sh" || true
    fi
    sed -e "s|__INSTALL_DIR__|${INSTALL_DIR}|g" \
        -e "s|__SPRING_PROFILE__|${AGENT_PROFILE}|g" \
      "${ROOT_DIR}/infra/launchd/com.retrysight.agent.plist" > "${TARGET}"
  fi
  UID_NUM="$(id -u)"
  launchctl bootout "gui/${UID_NUM}/com.retrysight.agent" 2>/dev/null || true
  launchctl unload "${TARGET}" 2>/dev/null || true
  launchctl bootstrap "gui/${UID_NUM}" "${TARGET}" 2>/dev/null || launchctl load -w "${TARGET}"
  echo "Installed launchd agent: ${TARGET} (profile=${AGENT_PROFILE})"
elif [[ "${OS}" == "Linux" ]]; then
  UNIT="${HOME}/.config/systemd/user/retrysight-agent.service"
  mkdir -p "$(dirname "${UNIT}")"
  if LAUNCHER="$(resolve_linux_launcher)"; then
    cat > "${UNIT}" <<EOF
[Unit]
Description=RetrySight Collector Agent
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
ExecStart=${LAUNCHER}
Restart=on-failure
RestartSec=5
Environment=SPRING_PROFILES_ACTIVE=${AGENT_PROFILE}

[Install]
WantedBy=default.target
EOF
  else
    if [[ ! -f "${INSTALL_DIR}/agent-app.jar" ]]; then
      echo "Packaging agent jar first..."
      "${ROOT_DIR}/scripts/package-agent.sh" || true
    fi
    sed -e "s|__INSTALL_DIR__|${INSTALL_DIR}|g" \
        -e "s|__SPRING_PROFILE__|${AGENT_PROFILE}|g" \
      "${ROOT_DIR}/infra/systemd/retrysight-agent-user.service" > "${UNIT}"
  fi
  systemctl --user daemon-reload
  systemctl --user enable --now retrysight-agent.service
  loginctl enable-linger "${USER}" >/dev/null 2>&1 || true
  echo "Installed user systemd unit: ${UNIT} (profile=${AGENT_PROFILE})"
else
  echo "Use infra/windows/install-agent-service.ps1 on Windows." >&2
  exit 1
fi

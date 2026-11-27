# macOS agent distribution (jpackage + signing)

Build unsigned installers with `./scripts/package-agent.sh`, then sign and notarize on a Mac with Apple Developer certificates.

## Prerequisites

- JDK 21+ with `jpackage`
- Apple Developer Program membership
- **Developer ID Application** certificate (sign `.app`)
- **Developer ID Installer** certificate (sign `.pkg`)
- `notarytool` credentials (`APPLE_NOTARY_PROFILE` keychain profile recommended)

## Build

```bash
```

Output: `release/agent-installers/RetrySightAgent-<version>.pkg` (jpackage `--app-version` uses `1.0.0` when product `VERSION` is pre-1.0, e.g. `0.1.0-rc.1`).

## Sign and notarize

```bash
export MACOS_APP_SIGN_IDENTITY='Developer ID Application: Your Org (TEAMID)'
export MACOS_INSTALLER_SIGN_IDENTITY='Developer ID Installer: Your Org (TEAMID)'
export APPLE_NOTARY_PROFILE='retrysight-notary'   # xcrun notarytool store-credentials

./scripts/sign-macos-agent.sh release/agent-installers/RetrySightAgent-0.1.0.pkg
```

Or set `MACOS_SIGN=1` when running `package-agent.sh` to invoke signing automatically after jpackage.

## Entitlements

`infra/installer/macos/entitlements.plist` enables network client access for manager uploads.

## CI (GitHub Actions)

The **Release** workflow (`.github/workflows/release.yml`) builds an unsigned `.pkg` on `macos-latest` for every PR and uploads it as an artifact.

For **signed + notarized** packages in CI:

1. Import `Developer ID Application` + `Developer ID Installer` p12 into a temporary keychain on the runner
2. Store notary credentials via `notarytool store-credentials`
3. Run `MACOS_SIGN=1 ./scripts/package-agent.sh`

See [RELEASE.md](RELEASE.md) for the full pipeline.

## Install layout

The PKG installs `RetrySightAgent.app` (bundled JRE) under `/Applications`.

**Auto-start:** the PKG `postinstall` registers `/Library/LaunchAgents/com.retrysight.agent.plist` with `RunAtLoad` + `KeepAlive`, so the collector starts at each user login after reboot and restarts on crash.

Manual / jar-based installs can still use:

```bash
./infra/scripts/install-agent-service.sh
```

Configure manager URL / API key via `~/.agent-retry/config.yaml` (`./scripts/retrysight-agent configure`).

IDE log collection may still require **Full Disk Access** in System Settings — the agent warns at startup via `MacOsPermissionPreflight`.

## Related docs

- [Agent buffer policy](AGENT_BUFFER_POLICY.md)
- [Deployment](DEPLOYMENT.md)

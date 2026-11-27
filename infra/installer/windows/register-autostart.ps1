# Copyright (c) 2026 Arka Prava Ghosh
# SPDX-License-Identifier: Apache-2.0
# Register RetrySightAgent to start at user logon (Scheduled Task).
# Used by MSI post-install CustomAction and infra/windows/install-agent-service.ps1.

param(
  [string]$LauncherPath = ""
)

$ErrorActionPreference = "Stop"
$TaskName = "RetrySightAgent"

function Find-Launcher {
  param([string]$Hint)
  if ($Hint -and (Test-Path -LiteralPath $Hint)) {
    return (Resolve-Path -LiteralPath $Hint).Path
  }
  $candidates = @(
    (Join-Path $PSScriptRoot "RetrySightAgent.exe"),
    (Join-Path $PSScriptRoot "..\RetrySightAgent.exe"),
    (Join-Path ${env:LOCALAPPDATA} "RetrySightAgent\RetrySightAgent.exe"),
    (Join-Path ${env:ProgramFiles} "RetrySightAgent\RetrySightAgent.exe"),
    (Join-Path ${env:LOCALAPPDATA} "Programs\RetrySightAgent\RetrySightAgent.exe")
  )
  foreach ($path in $candidates) {
    if ($path -and (Test-Path -LiteralPath $path)) {
      return (Resolve-Path -LiteralPath $path).Path
    }
  }
  return $null
}

$launcher = Find-Launcher -Hint $LauncherPath
if (-not $launcher) {
  Write-Warning "RetrySightAgent.exe not found; skipping auto-start registration."
  exit 0
}

$workDir = Split-Path -Parent $launcher
$action = New-ScheduledTaskAction -Execute $launcher -WorkingDirectory $workDir
$trigger = New-ScheduledTaskTrigger -AtLogOn
$settings = New-ScheduledTaskSettingsSet `
  -AllowStartIfOnBatteries `
  -DontStopIfGoingOnBatteries `
  -StartWhenAvailable `
  -RestartCount 3 `
  -RestartInterval (New-TimeSpan -Minutes 1)
$principal = New-ScheduledTaskPrincipal -UserId $env:USERNAME -LogonType Interactive -RunLevel Limited

Register-ScheduledTask `
  -TaskName $TaskName `
  -Action $action `
  -Trigger $trigger `
  -Settings $settings `
  -Principal $principal `
  -Force | Out-Null

# Start now so installers do not require a re-login to begin collecting.
Start-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue

Write-Host "Registered scheduled task '$TaskName' (AtLogOn) -> $launcher"

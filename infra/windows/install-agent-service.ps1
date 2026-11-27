# Copyright (c) 2026 Arka Prava Ghosh
# SPDX-License-Identifier: Apache-2.0
# Install jar (dev) or register auto-start for an existing jpackage install.

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$RegisterScript = Join-Path $Root "installer\windows\register-autostart.ps1"
$InstallDir = Join-Path $env:LOCALAPPDATA "RetrySightAgent"

$jpackageLauncher = @(
  (Join-Path $env:LOCALAPPDATA "Programs\RetrySightAgent\RetrySightAgent.exe"),
  (Join-Path $env:LOCALAPPDATA "RetrySightAgent\RetrySightAgent.exe"),
  (Join-Path $env:ProgramFiles "RetrySightAgent\RetrySightAgent.exe")
) | Where-Object { $_ -and (Test-Path -LiteralPath $_) } | Select-Object -First 1

if ($jpackageLauncher) {
  Write-Host "Found jpackage launcher: $jpackageLauncher"
  & $RegisterScript -LauncherPath $jpackageLauncher
  exit $LASTEXITCODE
}

$JarSource = Get-ChildItem "$Root\agent\agent-app\target\agent-app-*.jar" -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $JarSource) {
  Write-Error "Agent JAR not found. Run scripts/build.sh or scripts/package-agent.sh first (or install the MSI)."
}

New-Item -ItemType Directory -Force -Path $InstallDir | Out-Null
Copy-Item $JarSource.FullName (Join-Path $InstallDir "agent-app.jar") -Force

$TaskName = "RetrySightAgent"
$Action = New-ScheduledTaskAction -Execute "java" -Argument "-jar `"$(Join-Path $InstallDir 'agent-app.jar')`"" -WorkingDirectory $InstallDir
$Trigger = New-ScheduledTaskTrigger -AtLogOn
$Settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 1)
Register-ScheduledTask -TaskName $TaskName -Action $Action -Trigger $Trigger -Settings $Settings -Force | Out-Null
Start-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue

Write-Host "Installed agent to $InstallDir and registered scheduled task '$TaskName' (AtLogOn)."
Write-Host "Run scripts/retrysight-agent configure (or set ~/.agent-retry/config.yaml) before first collect."

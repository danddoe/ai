$ErrorActionPreference = 'Stop'

$pidFile = Join-Path $PSScriptRoot 'cockroach.pid'

if (!(Test-Path $pidFile)) {
  Write-Host 'No PID file found. If CockroachDB is running, stop it manually.'
  exit 0
}

# Do not use $pid — it is a read-only automatic variable (current PowerShell process id).
$crdbPid = Get-Content $pidFile | Select-Object -First 1
if (-not $crdbPid) {
  Remove-Item -Force $pidFile -ErrorAction SilentlyContinue
  Write-Host 'PID file was empty.'
  exit 0
}

try {
  $null = Get-Process -Id $crdbPid -ErrorAction Stop
  Write-Host "Stopping CockroachDB PID $crdbPid ..."
  Stop-Process -Id $crdbPid -Force
} catch {
  Write-Host "Process $crdbPid not found. Cleaning up PID file."
}

Remove-Item -Force $pidFile -ErrorAction SilentlyContinue
Write-Host 'Done.'

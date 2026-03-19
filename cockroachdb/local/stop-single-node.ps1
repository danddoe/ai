$ErrorActionPreference = 'Stop'

$pidFile = Join-Path $PSScriptRoot 'cockroach.pid'

if (!(Test-Path $pidFile)) {
  Write-Host 'No PID file found. If CockroachDB is running, stop it manually.'
  exit 0
}

$pid = Get-Content $pidFile | Select-Object -First 1
if (-not $pid) {
  Remove-Item -Force $pidFile -ErrorAction SilentlyContinue
  Write-Host 'PID file was empty.'
  exit 0
}

try {
  $p = Get-Process -Id $pid -ErrorAction Stop
  Write-Host "Stopping CockroachDB PID $pid ..."
  Stop-Process -Id $pid -Force
} catch {
  Write-Host "Process $pid not found. Cleaning up PID file."
}

Remove-Item -Force $pidFile -ErrorAction SilentlyContinue
Write-Host 'Done.'

param(
  [string]$CockroachPath = "",
  [string]$StoreDir = "$(Resolve-Path (Join-Path $PSScriptRoot "..\data"))",
  [string]$ListenAddr = "localhost:26257",
  [string]$HttpAddr = "localhost:8080"
)

$ErrorActionPreference = 'Stop'

function Resolve-CockroachExe {
  if ($CockroachPath -and (Test-Path $CockroachPath)) {
    return (Resolve-Path $CockroachPath).Path
  }

  $repoCockroach = Join-Path (Resolve-Path (Join-Path $PSScriptRoot ".." )).Path "bin\cockroach.exe"
  if (Test-Path $repoCockroach) {
    return $repoCockroach
  }

  $cmd = Get-Command cockroach -ErrorAction SilentlyContinue
  if ($cmd) {
    return $cmd.Source
  }

  throw "cockroach.exe not found. Install CockroachDB or place it at $repoCockroach"
}

$cockroachExe = Resolve-CockroachExe

New-Item -ItemType Directory -Force -Path $StoreDir | Out-Null

Write-Host "Starting CockroachDB single-node (insecure)..."
Write-Host "- SQL:  $ListenAddr"
Write-Host "- UI:   http://$HttpAddr"
Write-Host "- Data: $StoreDir"

$pidFile = Join-Path $PSScriptRoot "cockroach.pid"

# Start in background and save PID
$proc = Start-Process -FilePath $cockroachExe -PassThru -WindowStyle Hidden -ArgumentList @(
  'start-single-node',
  '--insecure',
  "--listen-addr=$ListenAddr",
  "--http-addr=$HttpAddr",
  "--store=$StoreDir"
)

Set-Content -Path $pidFile -Value $proc.Id -Encoding ASCII
Write-Host "Started (PID $($proc.Id))."

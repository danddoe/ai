param(
  [string]$CockroachPath = "",
  [string]$StoreDir = "",
  [string]$ListenAddr = "localhost:26257",
  [string]$HttpAddr = "localhost:8090"
)

$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
if (-not $StoreDir) {
  $StoreDir = Join-Path $repoRoot "cockroach-data"
}
New-Item -ItemType Directory -Force -Path $StoreDir | Out-Null
$StoreDir = (Resolve-Path -LiteralPath $StoreDir).Path

$versionFile = Join-Path $repoRoot "VERSION"
$recommended = $null
if (Test-Path $versionFile) {
  $recommended = (Get-Content $versionFile -Raw).Trim()
}

function Resolve-CockroachExe {
  if ($CockroachPath -and (Test-Path $CockroachPath)) {
    return (Resolve-Path $CockroachPath).Path
  }

  $repoCockroach = Join-Path $repoRoot "bin\cockroach.exe"
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

Write-Host "Starting CockroachDB single-node (insecure)..."
if ($recommended) {
  Write-Host "- Repo pin: v$recommended (see cockroachdb/VERSION and docker-compose.yml)"
}
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

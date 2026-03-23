# Start file-backed Vault (persistent data under vault/file-data/). Run from any directory.
$ErrorActionPreference = 'Stop'
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = (Resolve-Path (Join-Path $ScriptDir '..\..')).Path
Set-Location $RepoRoot
$ConfigPath = Join-Path $RepoRoot 'vault\config\file-persistent.hcl'
if (-not (Test-Path $ConfigPath)) {
    throw "Missing config: $ConfigPath"
}

Write-Host @"
Starting Vault with file storage (persistent).
  Repo root : $RepoRoot
  Config    : $ConfigPath
  Data dir  : $RepoRoot\vault\file-data (gitignored)

First time:
  `$env:VAULT_ADDR = 'http://127.0.0.1:8200'
  vault operator init -key-shares=1 -key-threshold=1
  vault operator unseal <unseal-key>
  vault login <root-token>
  vault secrets enable -version=2 -path=secret kv

After each restart: vault operator unseal, then login or set VAULT_TOKEN.

Ephemeral only (no disk): run manually: vault server -dev
"@
# Native exe parsing: -config=$var can pass literal "$ConfigPath" to Vault; use separate arg + call operator.
& vault server -config "$ConfigPath"

# Vault on Windows

## Install

1. Download `vault_*_windows_amd64.zip` from [HashiCorp Vault releases](https://releases.hashicorp.com/vault/).
2. Extract `vault.exe` and add its folder to your **PATH** (System Properties → Environment Variables), or invoke it by full path.

Verify:

```powershell
vault version
```

## Dev server (local, persistent)

Uses [`..\config\file-persistent.hcl`](../config/file-persistent.hcl); data lives under `vault/file-data/` (gitignored).

From this directory:

```powershell
.\dev-server.ps1
```

**First time:** in another window, `$env:VAULT_ADDR = 'http://127.0.0.1:8200'`, then `vault operator init -key-shares=1 -key-threshold=1`, `vault operator unseal`, `vault login`, and `vault secrets enable -version=2 -path=secret kv`. Save the unseal key and root token outside git.

**After each restart:** unseal again, then `vault login` or `$env:VAULT_TOKEN = '...'`.

For a throwaway in-memory server only: run `vault server -dev` yourself (not this script).

## WSL / Docker

If you run Vault inside WSL or a Linux container, use the scripts under [`../linux/`](../linux/) from that environment, or point `VAULT_ADDR` at the forwarded port.

## E2E password for Playwright

After storing `user_password` at `secret/erp/dev/e2e`:

```powershell
Set-Location <repo-root>
.\vault\windows\e2e-env.ps1
Set-Location erp-portal
npx playwright test
```

Requires `VAULT_ADDR`, `VAULT_TOKEN`, and `vault.exe` on `PATH`.

# Vault on Linux / macOS / WSL

## Install

1. Download the appropriate zip from [HashiCorp Vault releases](https://releases.hashicorp.com/vault/) (`linux_amd64`, `darwin_amd64`, or `arm64`).
2. Unzip and add the `vault` binary to your `PATH`.

## Dev server (local, persistent)

Uses [`../config/file-persistent.hcl`](../config/file-persistent.hcl); data lives under `vault/file-data/` (gitignored).

```bash
chmod +x dev-server.sh
./dev-server.sh
```

**First time:** in another terminal, `export VAULT_ADDR=http://127.0.0.1:8200`, then `vault operator init -key-shares=1 -key-threshold=1`, `vault operator unseal`, `vault login`, and `vault secrets enable -version=2 -path=secret kv`. Save the unseal key and root token outside git.

**After each restart:** unseal again, then `vault login` or set `VAULT_TOKEN`.

For a throwaway in-memory server only: run `vault server -dev` yourself (not this script).

## E2E password for Playwright

After storing `user_password` at `secret/erp/dev/e2e`:

```bash
source "$(git rev-parse --show-toplevel)/vault/linux/e2e-env.sh"
cd erp-portal && npx playwright test
```

Requires `VAULT_ADDR`, `VAULT_TOKEN`, and `vault` on `PATH`.

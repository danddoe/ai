# HashiCorp Vault (ERP)

Platform-specific helpers live in [`linux/`](linux/) and [`windows/`](windows/). **Do not commit tokens, unseal keys, or secret values** to git—only templates and example commands with placeholders.

## Persistent local Vault (default)

The [`dev-server` scripts](linux/dev-server.sh) use [`config/file-persistent.hcl`](config/file-persistent.hcl) with **file storage** under `vault/file-data/` (see [`.gitignore`](../.gitignore)). Data survives restarts; you **unseal** after each start.

Use **two terminals**: one for the Vault process (leave it running), one for the CLI (`vault operator …`, `vault login`, etc.).

### 1. Start the server

From repo root is not required—the scripts resolve the repo and `cd` there.

- **Windows:** `.\vault\windows\dev-server.ps1`
- **Linux / macOS / WSL:** `vault/linux/dev-server.sh`

Wait until Vault is listening on `http://127.0.0.1:8200` (see the listener in `file-persistent.hcl`).

### 2. First-time setup (second terminal)

Point the Vault CLI at the local API, then initialize once.

**Windows (PowerShell):**

```powershell
$env:VAULT_ADDR = 'http://127.0.0.1:8200'
vault operator init -key-shares=1 -key-threshold=1
```

Copy the **Unseal Key** and **Initial Root Token** into a password manager. **Do not commit them to git.**

```powershell
vault operator unseal
# paste the unseal key when prompted

vault login
# paste the root token when prompted

vault secrets enable -version=2 -path=secret kv
```

**Linux / macOS / WSL:**

```bash
export VAULT_ADDR=http://127.0.0.1:8200
vault operator init -key-shares=1 -key-threshold=1
```

Store the **unseal key** and **root token** in a password manager (not in git).

```bash
vault operator unseal
vault login
vault secrets enable -version=2 -path=secret kv
```

If `operator init` reports the cluster is already initialized, skip init and only unseal / log in.

### 3. Verify

```bash
vault status
```

You want **Initialized: true** and **Sealed: false** before using the HTTP API or Spring `vault://` imports.

### 4. Every restart

Start the dev server again, then `vault operator unseal` with the same key, then `vault login` or set `VAULT_TOKEN` (`export VAULT_TOKEN=...` on Unix, `$env:VAULT_TOKEN = '...'` in PowerShell).

When you are ready, load application secrets with the [KV examples below](#kv-v2-layout-convention) (`vault kv put …`).

In-memory **`vault server -dev`** is still available manually for quick throwaway sessions; it does not use this config.

## Environment variables

| Variable | Purpose |
|----------|---------|
| `VAULT_ADDR` | Vault API URL (e.g. `http://127.0.0.1:8200` for local file-backed server) |
| `VAULT_TOKEN` | Token after `vault login` (or root token from `operator init`); use AppRole or other auth in production |

## KV v2 layout (convention)

Mount: `secret` (KV v2). Example paths for profile `dev`:

| Path (logical) | Contents (keys are Spring property names unless noted) |
|----------------|--------------------------------------------------------|
| `erp/dev/common` | Shared: `spring.datasource.url`, `spring.datasource.username`, `spring.datasource.password`, `app.jwt.hmac-secret`, `gateway.portal-host.trust-secret` |
| `erp/dev/iam` | IAM-only: `app.bootstrap.admin-password` (maps to seed superadmin) |
| `erp/dev/entity-builder` | `entitybuilder.pii.key` |
| `erp/dev/e2e` | `user_password` — for Playwright only; use [`linux/e2e-env.sh`](linux/e2e-env.sh) / [`windows/e2e-env.ps1`](windows/e2e-env.ps1) |

Services use `spring.config.import=optional:vault://...` so the app starts without Vault; when `VAULT_TOKEN` and paths are populated, secrets load at startup.

### Example: seed common secret (dev)

Replace values before running:

```bash
vault kv put secret/erp/dev/common \
  spring.datasource.url="jdbc:postgresql://localhost:26257/defaultdb?sslmode=disable" \
  spring.datasource.username="root" \
  spring.datasource.password="YOUR_DB_PASSWORD" \
  app.jwt.hmac-secret="YOUR_HS256_SECRET_MIN_32_BYTES" \
  gateway.portal-host.trust-secret="YOUR_GATEWAY_TRUST_SECRET"
```

### Example: IAM bootstrap password

```bash
vault kv put secret/erp/dev/iam \
  app.bootstrap.admin-password="YOUR_SUPERADMIN_PASSWORD"
```

### Example: entity-builder PII key

```bash
vault kv put secret/erp/dev/entity-builder \
  entitybuilder.pii.key="YOUR_32_BYTE_MIN_PII_KEY_MATERIAL"
```

Use the same logical path pattern for `staging` / `prod` with different `VAULT_ADDR` and policies.

## Local Spring Boot without Vault

There are **no secret defaults in the repo**. Set `JWT_SECRET`, `SEED_SUPERADMIN_PASSWORD` (for `default-bootstrap`), `PII_KEY` (entity-builder), `DATABASE_*`, etc., via environment or populate Vault. Profile `dev` only activates `application-dev.yml` markers—it does not inject passwords.

```bash
# Example: export secrets in your shell, then run (values are yours, not committed).
./gradlew :iam:bootRun --args='--spring.profiles.active=dev,default-bootstrap'
```

## Playwright E2E

From repo root, with Vault CLI logged in and secret `secret/erp/dev/e2e` containing `user_password`:

- Linux / macOS / WSL: `source vault/linux/e2e-env.sh` then run Playwright from `erp-portal/`.
- Windows (PowerShell): `.\vault\windows\e2e-env.ps1` then run Playwright.

Or use Vault Agent to render `E2E_USER_PASSWORD` into the environment before `npx playwright test`.

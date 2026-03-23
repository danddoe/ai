# ERP portal

React + TypeScript SPA: **login**, **entity list**, **layout list**, and a **three-panel form builder** (data dictionary, structure editor, presentation) for `version: 2` region layouts via the API gateway.

## Prerequisites

- **Node.js 20+** (includes **npm**). If PowerShell says `npm` is not recognized, Node is missing or not on your `PATH`.

### Install Node.js on Windows

1. **Installer (simplest):** [https://nodejs.org](https://nodejs.org) → **LTS** → run the `.msi` → leave **“Add to PATH”** checked → finish and **close and reopen** PowerShell (or Cursor).
2. **winget:** `winget install OpenJS.NodeJS.LTS` (may prompt for administrator approval).
3. Confirm: `node -v` and `npm -v` should print versions.

If Node is installed but `npm` still fails, sign out of Windows or reboot so `PATH` updates, or add `C:\Program Files\nodejs\` manually to your user **PATH** (where `node.exe` lives).

Also required:

- CockroachDB, **IAM**, **entity-builder**, **global-search** (optional but required for Cmd+K omnibox), **api-gateway** running
- IAM: **`AUTH_REFRESH_TOKEN_IN_COOKIE`** defaults to **`true`** (httpOnly `erp_refresh`; JSON has no `refreshToken`). Set **`false`** only for non-browser API clients; the portal expects cookie mode.

## Run

```bash
cd erp-portal
npm install
npm run dev
```

### PowerShell: “running scripts is disabled” / `npm.ps1`

PowerShell may block `npm.ps1` under strict **execution policies**. Use either:

- `npm.cmd run dev` (or `npm.cmd install`), or  
- `Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser` once, then `npm run dev`, or  
- run the same commands in **cmd.exe** instead of PowerShell.

Open `http://localhost:5173`. Sign in with tenant slug, email, password, then use **Entities** → **Form layouts** → **Builder**. **Cmd+K** opens the omnibox (`cmdk`): debounced server search via `GET /v1/search/omnibox` (navigation + indexed records). Run `npm install` after pulling to pick up the `cmdk` dependency.

### Routes (deep links)

| Path | Purpose |
|------|---------|
| `/login` | Sign in (`?` not used; return path stored in router state when redirected from a protected page) |
| `/home` | Landing (module cards from IAM navigation) |
| `/entities` | Entity list |
| `/entities/:entityId/layouts` | Layouts for an entity; new blank / from template |
| `/entities/:entityId/layouts/:layoutId` | **Form builder** — full-width layout; module sidebar hidden; global header + search remain |

**Shell:** All authenticated routes use [`AppShell`](src/layouts/AppShell.tsx): sticky header (logo, **global search** Ctrl+K / ⌘K, log out), collapsible **IAM-driven sidebar** (`GET /v1/navigation`), and main content. On form-builder URLs the **module sidebar is hidden** and a “Form builder” badge appears; the builder keeps its own three-panel tool UI.

If IAM was started with profile `default-bootstrap` on an empty DB, use tenant **`ai`** and **`superadmin@ai.com`**; the password is whatever you configured via **`SEED_SUPERADMIN_PASSWORD`** / Vault (no default in this repo).

`VITE_API_BASE_URL` defaults to `http://localhost:8000` in `.env.development`.

### `401 Unauthorized` on API calls

- **Same `JWT_SECRET` / `app.jwt.hmac-secret`** (and usually `JWT_ISSUER` / `JWT_AUDIENCE`) on **IAM** and **entity-builder**, configured explicitly or via Vault.
- **Log in again** after a full reload: the access token is memory-only; refresh relies on the httpOnly cookie from login.
- Confirm **api-gateway** (8000), **IAM** (8080), **entity-builder** (8081) are running.

### `403 Forbidden` on `GET /v1/entities`

The user needs **`entity_builder:schema:read`** (bootstrap `SUPERADMIN` includes it).

## E2E: global search (live)

End-to-end checks against **real** services: portal → gateway → IAM + **global-search** omnibox. Requires the stack from [Prerequisites](#prerequisites), including **global-search** on the gateway route.

1. Install browser binaries once (after `npm install`):

   ```bash
   npx playwright install chromium
   ```

2. Start the API gateway, IAM, entity-builder, global-search, DB, and the Vite dev server (`npm run dev`), **or** let Playwright start Vite by setting `PORTAL_E2E_SERVE=1` (PowerShell: `$env:PORTAL_E2E_SERVE='1'`).

3. Run:

   ```bash
   npm run test:e2e
   ```

Required for E2E: **`E2E_USER_PASSWORD`** (must match the IAM user you log in as—e.g. from Vault via `vault/linux/e2e-env.sh`). Optional: `PORTAL_BASE_URL`, `E2E_TENANT_SLUG`, `E2E_USER_EMAIL`.

- `npm run test:e2e:headed` — visible browser  
- `npm run test:e2e:ui` — Playwright UI mode  

Tests live in [`e2e/global-search.spec.ts`](e2e/global-search.spec.ts).

## Build (static)

```bash
npm run build
```

Serve `dist/` with **fallback to `index.html`** for client-side routes.

## Auth

- **Access token:** in memory only (React state + API client); full page reload requires signing in again unless you add a silent refresh on boot.
- **Refresh token:** `httpOnly` cookie `erp_refresh` when IAM cookie mode is on (default); SPA does not read it.
- API calls use `credentials: 'include'` and `Authorization: Bearer` when an access token exists.
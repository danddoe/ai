# IAM Module

Identity and Access Management for the multi-tenant ERP. Built with Spring Boot, CockroachDB (PostgreSQL-compatible), and JWT.
Spring Boot service (default port **8080**
## Requirements

- **Java 17** or newer
- **CockroachDB** (standard SQL target for Flyway migrations and local dev)

## Configuration

Set via environment or `application.yml`:

| Variable | Description | Default |
|----------|-------------|---------|
| `DATABASE_URL` | JDBC URL | `jdbc:postgresql://localhost:26257/defaultdb?sslmode=disable` |
| `DATABASE_USER` | DB user | `root` |
| `DATABASE_PASSWORD` | DB password | (empty) |
| `JWT_SECRET` | HMAC secret for JWT (min 256 bits for HS256) | dev default |
| `JWT_ISSUER` | JWT `iss` claim | `erp-iam` |
| `JWT_AUDIENCE` | JWT `aud` claim | `erp-api` |
| (contract) | Full claim list + TCK for resource servers | [`design/JWT_access_token_contract.md`](../design/JWT_access_token_contract.md) |
| `SERVER_PORT` | HTTP port | `8080` |
| `AUTH_REFRESH_TOKEN_IN_COOKIE` | Put refresh JWT in httpOnly cookie `erp_refresh`; omit `refreshToken` from JSON | `true` |
| `AUTH_REFRESH_COOKIE_SECURE` | Set `Secure` on refresh cookie (use `true` behind HTTPS) | `false` |

## Database / Flyway

IAM uses **`flyway_iam_schema_history`** (not the default `flyway_schema_history`) so it does not share version rows with entity-builder on the same database. `baseline-on-migrate` with `baseline-version: 0` supports a non-empty database while still applying IAM’s `V1`…`V10` migrations.

Migrations **`V1`–`V6`** use **`CREATE TABLE IF NOT EXISTS`** / **`CREATE INDEX IF NOT EXISTS`** so a half-applied or repaired database can finish without “relation already exists”. If those files change after Flyway already recorded them, run profile **`repair-flyway`** once to refresh checksums.

If you change an already-applied migration file, either revert the file or start once with profile **`repair-flyway`** (`application-repair-flyway.yml`). Prefer new versioned scripts for further DDL.

**`Detected failed migration to version …` / `half-completed changes`:** A previous run marked migration V*n* as failed in `flyway_iam_schema_history`. Start IAM **once** with repair so Flyway can fix history, then start normally:

```powershell
.\gradlew :iam:bootRun --args="--spring.profiles.active=repair-flyway"
```

If V1 failed mid-way, you may still have a **partial** `tenants` table (or other objects). After repair, if migrate errors with “already exists”, drop the partial IAM tables (or use a fresh database), then run IAM again without `repair-flyway`. In Cockroach you can inspect with `SHOW TABLES FROM defaultdb;` and drop only what’s safe.

**Already have IAM tables** (from an older shared `flyway_schema_history` run)? Flyway will try to run `V1__…` again and can fail with “relation already exists”. Use a clean database, drop the IAM objects if safe, or baseline `flyway_iam_schema_history` to match your real schema (see [Flyway baseline](https://documentation.red-gate.com/flyway)).

## Build and run

From project root:

```bash
# Build
./gradlew :iam:build

# Run (with devtools for auto-reload)

./gradlew :iam:bootRun -Dorg.gradle.java.home=C:/java/jdk-17.0.2

C:\java\cockroach-v19.1.1\cockroach.exe" sql --insecure --host=localhost --port=26257 -e "SELECT id, name, slug, status FROM tenants ORDER BY slug;"


```

## API

- **POST /auth/login** – Login (body: `tenantSlugOrId`, `email`, `password`). Returns `accessToken`, `expiresInSeconds`. With `AUTH_REFRESH_TOKEN_IN_COOKIE=true` (default), sets httpOnly cookie `erp_refresh` and omits `refreshToken` from JSON.
- **POST /auth/refresh** – Refresh. With cookie mode, send empty body `{}` and `Cookie` (browser); otherwise body `{ "refreshToken" }`. Returns new `accessToken`; cookie is rotated when cookie mode is on.
- **POST /auth/logout** – Logout. With cookie mode, cookie is cleared; optional body `refreshToken` for non-cookie clients.
- **GET /.well-known/jwks.json** – JWKS (empty when using HMAC; use for RSA in production).

Protected endpoints require header: `Authorization: Bearer <accessToken>`.

## Portal navigation (global ERP shell)

- **GET /v1/navigation** – Returns a **nested tree** of portal routes for the **current user**. Rows are stored in `portal_navigation_items` (see Flyway `V11`/`V12`). Each row can include:
  - `route_path` (SPA path; nullable for `section` / `divider`)
  - `label`, `description`, `type` (`internal` \| `external` \| `section` \| `divider`), `icon` (string key for the SPA to map)
  - `category_key` (optional module bucket, e.g. `entity_builder`, `accounting`, `accounts_payable`; inherited down the tree in API responses when omitted)
  - `search_keywords` (JSON array of strings)
  - `required_permissions` / `required_roles` (JSON arrays; **OR** within each list; **AND** between lists when both are non-empty)
  - `parent_id`, `sort_order`, `is_active`

Filtering is done in IAM using JWT **permissions** and **role names** (same strings as in the access token). This endpoint is **navigation only**; every module must still enforce authorization on its APIs.

- **GET /v1/navigation/search?q=&limit=** – Flat list of **internal** / **external** items the user may see (same RBAC as the tree), matching `label`, `description`, or `search_keywords` (case-insensitive substring). Used by the **global-search** omnibox BFF.

- **POST /v1/navigation/items** – Create a nav row (`route_path`, `label`, `scope` `TENANT` \| `GLOBAL`, optional `parent_id`, etc.). **TENANT** scope: caller must have `portal:navigation:write` or `entity_builder:schema:write`; row `tenant_id` is set from the JWT. **GLOBAL**: `iam:security:admin` or `iam:superadmin`; `tenant_id` is null. `route_path` is restricted to entity record URLs (see `PortalNavigationAdminService`).

- **PATCH /v1/navigation/items/{id}** – Update fields; **`promoteToGlobal: true`** clears `tenant_id` (admin only).

- **DELETE /v1/navigation/items/{id}** – Soft-deactivate (`is_active = false`) with the same ownership rules as PATCH.

## REST API (v1) – IAM administration

Documentation endpoints (AI/automation-friendly):

- **GET /v3/api-docs** – OpenAPI JSON
- **GET /swagger-ui/index.html** – Swagger UI
- **GET /v1/ai/guide** – Machine-readable workflow guide (JSON)

Core resources:

- **Tenants (cross-tenant admin)**: `/v1/tenants`
- **Users (global identity, admin-only)**: `/v1/users`
- **Tenant memberships**: `/v1/tenants/{tenantId}/members`
- **Roles**: `/v1/tenants/{tenantId}/roles`
- **Permissions (global registry)**: `/v1/permissions`
- **Assignments**:
  - Role permissions: `/v1/tenants/{tenantId}/roles/{roleId}/permissions`
  - User roles: `/v1/tenants/{tenantId}/users/{userId}/roles`

Authorization:

- Tenant-scoped routes require the caller’s JWT `tenant_id` to match `{tenantId}` **unless** the caller has `iam:security:admin`.
- Cross-tenant tenant operations require `iam:tenants:admin`.
- Permission registry write operations require `iam:permissions:admin`.

## JWT claims (for ERP modules)

- `sub` – user ID (UUID)
- `tenant_id` – current tenant ID (UUID)
- `email` – user email
- `roles` – list of role names
- `permissions` – list of permission codes

Validate tokens using the same secret (HMAC) or the public key from JWKS (RSA).

## Database schema

Flyway migrations in `src/main/resources/db/migration/`:

- `tenants`, `users`, `tenant_users`, `roles`, `permissions`, `role_permissions`, `user_roles`, `refresh_tokens`, `audit_log`
- Seed permissions in V7 and V8.

**Optional bootstrap (local dev):** Run with profile `default-bootstrap` when the database has **no tenants** yet. This creates tenant **AI** (`slug` **`ai`**) and a **SUPERADMIN** user with every seeded permission (IAM + entity-builder, including `iam:superadmin`). It also creates a separate **system builder** user **`superai@unknownerp.com`** in the same tenant with the same **SUPERADMIN** role (same permissions). That user’s password defaults to **`SEED_SUPERADMIN_PASSWORD`** unless you set **`SEED_SUPERAI_PASSWORD`**.

```bash
./gradlew :iam:bootRun --args='--spring.profiles.active=default-bootstrap'
.\gradlew.bat :iam:bootRun --args="--spring.profiles.active=default-bootstrap"
```

After bootstrap, default **tenant** slug is **`ai`**, primary admin **email** **`superadmin@ai.com`**, and system builder **`superai@unknownerp.com`**. Passwords are **only** what you set in **`SEED_SUPERADMIN_PASSWORD`** / **`app.bootstrap.admin-password`** (and optionally **`SEED_SUPERAI_PASSWORD`** for the builder account) or Vault—there is no default in the repository.

If login returns **“Tenant not found”**, no row matches that slug/UUID: either run bootstrap on an **empty** DB (`--spring.profiles.active=default-bootstrap`) or enter the slug of a tenant that already exists. Slugs like `default` only exist if you created them.

Override names/email via `app.bootstrap.*` in `application.yml`. If tenants already exist, bootstrap does nothing.

Otherwise, create a tenant and user via SQL or admin APIs, then add `tenant_users` and roles.

**Login example** (PowerShell; do not commit real passwords):

```powershell
$body = @{ tenantSlugOrId = 'ai'; email = 'superadmin@ai.com'; password = $env:SEED_SUPERADMIN_PASSWORD } | ConvertTo-Json
Invoke-RestMethod -Uri 'http://127.0.0.1:8080/auth/login' -Method Post -Body $body -ContentType 'application/json'
```
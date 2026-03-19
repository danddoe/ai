# IAM Module

Identity and Access Management for the multi-tenant ERP. Built with Spring Boot, CockroachDB (PostgreSQL-compatible), and JWT.

## Requirements

- **Java 17** or newer
- CockroachDB (or PostgreSQL for local dev)

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
| `SERVER_PORT` | HTTP port | `8080` |

## Database / Flyway

IAM uses **`flyway_iam_schema_history`** (not the default `flyway_schema_history`) so it does not share version rows with entity-builder on the same database. `baseline-on-migrate` with `baseline-version: 0` supports a non-empty database while still applying IAM’s `V1`…`V10` migrations.

If you change an already-applied migration file, either revert the file or start once with profile **`repair-flyway`** (`application-repair-flyway.yml`). Prefer new versioned scripts for further DDL.

**Already have IAM tables** (from an older shared `flyway_schema_history` run)? Flyway will try to run `V1__…` again and can fail with “relation already exists”. Use a clean database, drop the IAM objects if safe, or baseline `flyway_iam_schema_history` to match your real schema (see [Flyway baseline](https://documentation.red-gate.com/flyway)).

## Build and run

From project root:

```bash
# Build
./gradlew :iam:build

# Run (with devtools for auto-reload)

./gradlew :iam:bootRun -Dorg.gradle.java.home=C:/java/jdk-17.0.2


```

## API

- **POST /auth/login** – Login (body: `tenantSlugOrId`, `email`, `password`). Returns `accessToken`, `refreshToken`, `expiresInSeconds`.
- **POST /auth/refresh** – Refresh (body: `refreshToken`). Returns new tokens.
- **POST /auth/logout** – Logout (body: `refreshToken`). Revokes the refresh token.
- **GET /.well-known/jwks.json** – JWKS (empty when using HMAC; use for RSA in production).

Protected endpoints require header: `Authorization: Bearer <accessToken>`.

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

**Optional bootstrap (local dev):** Run with profile `default-bootstrap` to create a default tenant and admin user:

```bash
./gradlew :iam:bootRun --args='--spring.profiles.active=default-bootstrap' 
```

Then login with tenant slug `default`, email `admin@example.com`, password `admin123`. Otherwise, create a tenant and user via SQL or a bootstrap endpoint, then add a `tenant_users` row and assign roles.

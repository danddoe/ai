# core-service

Spring Boot service (default port **8084**) for **tenant org master data**: companies, business units, regions, locations, properties, property units, countries, and **portal** host bindings plus `GET /v1/portal/bootstrap` for SPA host resolution. JWTs are validated with the same HMAC contract as IAM and other resource servers.

Traffic from operators typically goes through **api-gateway** (see [`api-gateway/README.md`](../api-gateway/README.md)); gateway env **`CORE_SERVICE_URL`** defaults to `http://localhost:8084`.

## Requirements

- **Java 17** or newer
- **CockroachDB** (PostgreSQL wire protocol; Hibernate uses `CockroachDialect`)

## Build and run

From the repository root:

```bash
./gradlew :core-service:build
./gradlew :core-service:bootRun
```

Activate Spring profile **`dev`** when you use `application-dev.yml` markers:

```bash
./gradlew :core-service:bootRun --args='--spring.profiles.active=dev'
```

## Configuration

Set via environment variables or your own `application-*.yml` (do not commit secrets). `application.yml` binds datasource, JWT, optional Vault, and gateway trust.

| Variable | Description |
|----------|-------------|
| `DATABASE_URL` | JDBC URL (Cockroach / Postgres) |
| `DATABASE_USER` | DB user |
| `DATABASE_PASSWORD` | DB password |
| `SERVER_PORT` | HTTP port (`8084` if unset) |
| `JWT_SECRET` | HMAC secret for access tokens (must match IAM signing key) |
| `JWT_ISSUER` | Expected JWT `iss` (`erp-iam` if unset) |
| `JWT_AUDIENCE` | Expected JWT `aud` (`erp-api` if unset) |
| `GATEWAY_TRUST_SECRET` | When set (and matching the gateway), trusted `X-Resolved-*` headers are honored and cross-checked with the JWT tenant |
| `VAULT_ADDR` | Vault API URL (`http://127.0.0.1:8200` if unset) |
| `VAULT_TOKEN` | Vault token (empty = no Vault auth) |
| `SPRING_VAULT_ENV` | Path segment for `secret/erp/{env}/…` (`dev` if unset) |

Optional Vault imports (non-fatal if missing): `secret/erp/{env}/common` and `secret/erp/{env}/core-service`. See [`vault/README.md`](../vault/README.md).

JWT shape and cross-module tests: [`design/JWT_access_token_contract.md`](../design/JWT_access_token_contract.md).

## Database / Flyway

- Migrations live under `src/main/resources/db/migration/`.
- Flyway uses its **own history table** `flyway_core_org_schema_history` so version numbers do not collide with IAM (`flyway_iam_schema_history`) or entity-builder on a **shared** database.
- `baseline-on-migrate` with `baseline-version: 0` supports a non-empty database while still applying this module’s scripts.

If you change an already-applied migration file, follow the same repair/baseline discipline as other modules (prefer new `V{n}__…` files instead of editing applied SQL).

## HTTP API

| Area | Base path |
|------|-----------|
| Portal bootstrap (unauthenticated) | `GET /v1/portal/bootstrap` |
| Countries | `/v1/countries` |
| Tenant-scoped org data | `/v1/tenants/{tenantId}/companies`, `…/business-units`, `…/regions`, `…/locations`, `…/properties`, `…/property-units`, `…/portal-host-bindings` |

Authenticated routes expect:

`Authorization: Bearer <accessToken>`

IAM permission codes follow the `master_data:…` pattern (see e2e fixtures in `CoreService*E2ETest` for examples).

**OpenAPI:** `GET /v3/api-docs`  
**Swagger UI:** `GET /swagger-ui/index.html`

## Tests

Unit and integration tests run with `./gradlew :core-service:test`.

**E2E** tests (`CoreService*E2ETest`) expect a reachable Cockroach instance. They create a throwaway database per run. Override with `COCKROACH_HOST` and `COCKROACH_PORT` (default `localhost:26257`). The test JVM starts the app with a fixed dev JWT secret; see `AbstractCoreServiceE2ETest` for the value used when minting tokens.

## Related modules

- **api-gateway** — routes `/v1/countries`, `/v1/portal`, and tenant master-data paths to core-service.
- **entity-builder** — optional EAV extensions; system catalog can be generated from JPA entities including core-service (see [`entity-builder/README.md`](../entity-builder/README.md)).

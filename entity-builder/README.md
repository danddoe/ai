# entity-builder

Dynamic entity schema + record storage for the multi-tenant ERP.

## Database / Flyway

- **Shared Cockroach/Postgres with IAM:** entity-builder uses `flyway_entity_builder_schema_history` and IAM uses `flyway_iam_schema_history`, so version numbers never collide on the default `flyway_schema_history`. `baseline-on-migrate` + `baseline-version: 0` allows a non-empty database while still running this module’s SQL.
- **Checksum validation errors** after editing an already-applied migration: start once with profile `repair-flyway` (see `application-repair-flyway.yml`), then start normally without that profile. Prefer adding a new `V3__…` migration instead of changing old files.

## AI/automation documentation

- OpenAPI: `GET /v3/api-docs`
- Swagger UI: `GET /swagger-ui/index.html`
- Machine-readable guide: `GET /v1/ai/guide`

## Auth

All `/v1/**` endpoints require:

`Authorization: Bearer <accessToken>`

JWT tenant context comes from the token claim `tenant_id`.

## Key permissions (IAM)

The API uses IAM permissions from the JWT `permissions` claim as Spring Security authorities.

Minimum permission codes (v1):

- `entity_builder:schema:read`
- `entity_builder:schema:write`
- `entity_builder:records:read`
- `entity_builder:records:write`
- `entity_builder:pii:read` (decrypt/return raw PII fields; otherwise PII is masked)

## Main endpoints (v1)

Schema:

- `POST /v1/entities`
- `GET /v1/entities/{entityId}`
- `PATCH /v1/entities/{entityId}`
- `DELETE /v1/entities/{entityId}`
- `POST /v1/entities/{entityId}/fields`
- `PATCH /v1/entities/{entityId}/fields/{fieldId}`
- `DELETE /v1/entities/{entityId}/fields/{fieldId}`
- `POST /v1/entities/{baseEntityId}/extensions`
- `GET /v1/entities/{baseEntityId}/extensions`
- `DELETE /v1/entities/{baseEntityId}/extensions/{extensionEntityId}`
- `POST /v1/entity-relationships`
- `GET /v1/entity-relationships`

Records:

- `POST /v1/tenants/{tenantId}/entities/{entityId}/records`
  - Optional header: `Idempotency-Key`
- `GET /v1/tenants/{tenantId}/entities/{entityId}/records`
- `GET /v1/tenants/{tenantId}/entities/{entityId}/records/{recordId}`
- `PATCH /v1/tenants/{tenantId}/entities/{entityId}/records/{recordId}`
- `DELETE /v1/tenants/{tenantId}/entities/{entityId}/records/{recordId}`

Form layouts:

- `GET /v1/entities/{entityId}/form-layouts`
- `POST /v1/entities/{entityId}/form-layouts`
- `GET /v1/entities/{entityId}/form-layouts/{layoutId}`

## E2E tests

End-to-end system tests in `src/test/java/com/erp/entitybuilder/e2e/` exercise each service against a real CockroachDB instance. **CockroachDB must be running** (see `cockroachdb/README.md`).

```bash
./gradlew :entity-builder:test --tests "com.erp.entitybuilder.e2e.*"
```

If CockroachDB is not reachable, E2E tests are skipped.


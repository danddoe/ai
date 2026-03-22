# entity-builder

Dynamic entity schema + record storage for the multi-tenant ERP.
Spring Boot service (default port **8082**)
## Database / Flyway

- **CockroachDB is the standard engine** (same shared cluster as IAM is fine). SQL avoids partial unique indexes and expression-only unique indexes (unsupported); some constraints use **STORED computed columns** + plain unique indexes instead. PostgreSQL may work for dev if DDL matches, but it is not the reference.
- **Shared DB with IAM:** entity-builder uses `flyway_entity_builder_schema_history` and IAM uses `flyway_iam_schema_history`, so version numbers never collide on the default `flyway_schema_history`. `baseline-on-migrate` + `baseline-version: 0` allows a non-empty database while still running this module’s SQL.
- **Checksum validation errors** after editing an already-applied migration: start once with profile `repair-flyway` (see `application-repair-flyway.yml`), then start normally without that profile. Prefer adding a new `V{n}__…` migration instead of changing files that are already applied in production.
- **CockroachDB + Flyway “Waiting for lock on Flyway schema history table” / `InsertRowLock`:** On Cockroach, Flyway emulates a lock with a special row (`description` = `flyway-lock`, `installed_rank` &lt; 0) in **`flyway_entity_builder_schema_history`**. If the JVM stops during migration (crash, **Spring Boot DevTools restart**, or two `bootRun` instances), that row can remain while another process waits forever. **Fix:** stop every entity-builder instance, then remove only the lock row:
  ```sql
  DELETE FROM flyway_entity_builder_schema_history
  WHERE description = 'flyway-lock' OR installed_rank < 0;
  ```
  Restart a single instance. **Prevention:** entity-builder ships with `spring.devtools.restart.enabled=false` so DevTools does not restart the app mid-Flyway (the usual cause). To turn classpath restart back on: `SPRING_DEVTOOLS_RESTART_ENABLED=true` (expect possible lock issues on Cockroach).

## AI/automation documentation

- OpenAPI: `GET /v3/api-docs`
- Swagger UI: `GET /swagger-ui/index.html`
- Machine-readable guide: `GET /v1/ai/guide`

## Auth

All `/v1/**` endpoints require:

`Authorization: Bearer <accessToken>`

JWT tenant context comes from the token claim `tenant_id`.

Token shape, env vars (`JWT_SECRET`, `JWT_ISSUER`, `JWT_AUDIENCE`), and cross-module tests: [`design/JWT_access_token_contract.md`](../design/JWT_access_token_contract.md).

## Key permissions (IAM)

The API uses IAM permissions from the JWT `permissions` claim as Spring Security authorities.

Minimum permission codes (v1):

- `entity_builder:schema:read`
- `entity_builder:schema:write`
- `entity_builder:records:read`
- `entity_builder:records:write`
- `entity_builder:pii:read` (decrypt/return raw PII fields; otherwise PII is masked)

## Hybrid domain core + EAV extensions

Some entities describe **both** columns owned by another service (e.g. `loans-module` / `loan_applications`) and **tenant extensions** stored here:

- **`entity_fields.config.storage`:** `CORE_DOMAIN` (metadata only; no `entity_record_values` row) or `EAV_EXTENSION` (default). See [`design/EntityField_storage_contract.md`](../design/EntityField_storage_contract.md).
- **Record APIs** reject payloads that include `CORE_DOMAIN` slugs; required-field validation applies only to EAV fields.
- **GET record** `values` omit core slugs (merge core DTOs in the portal or a BFF).
- **Lookup by domain id:** `GET /v1/tenants/{tenantId}/entities/{entityId}/records/by-external-id/{externalId}` (e.g. `externalId` = loan UUID string).
- **Resolve entity id by slug:** `GET /v1/entities/by-slug/{slug}`.
- **Classpath manifests:** `src/main/resources/system-entity-catalog/*.json` — apply with `POST /v1/tenants/{tenantId}/catalog/sync?manifestKey=loan_application` (`entity_builder:schema:write`). To scaffold manifests from JPA (`core-service` / `loans-module`), add each entity to [`entity-catalog-generator/catalog-spec.json`](../entity-catalog-generator/catalog-spec.json) and run `gradlew :entity-catalog-generator:generateEntityCatalog`. **Only spec-listed entities are written**; existing `*.json` files are **overwritten** by default (`-PcatalogGen.overwrite=false` to skip). Use `-PcatalogGen.updateIndex=true` to append new manifest keys to `index.json` (on PowerShell quote `-P…=true`). Non–JPA fields (e.g. `EAV_EXTENSION`) can stay in spec via `extraFields` on that entity.

## Search and lookups

- **`global_search_documents`** — Cross-entity omnibox index (Flyway `V7__global_search_documents.sql`): one row per dynamic record with `title`, `subtitle`, portal `route_path`, and `search_text` (trigram index). Upserted when `search_vector` is recomputed; removed when the record is deleted. Read via `GET /v1/tenants/{tenantId}/search/records?q=...` (same auth as records read). The **global-search** BFF aggregates this with IAM navigation search at `GET /v1/search/omnibox`.
- **`entity_records.search_vector`** — Denormalized, lowercased text built from current record values for fields that opt in via **`entity_fields.config`**. Only fields with `isSearchable: true` or `is_searchable: true` in JSON config contribute. **PII fields never contribute** (even if config says searchable); plaintext PII must not appear in the vector.
- **Trigram index** — `search_vector` is indexed for fast `ILIKE` prefix/substring style matching (see Flyway `V6__entity_records_search_vector.sql`).
- **`GET /v1/tenants/{tenantId}/entities/{entityId}/records/lookup`** — Query params: `term` (required), optional `limit`, optional repeated `displaySlugs`. Returns matching record ids, display label, and selected value columns. Requires `entity_builder:records:read`.
- **`entities.default_display_field_slug`** — Optional slug of the field used as the primary display label for lookups and UIs. Set or clear via `PATCH /v1/entities/{entityId}` with `defaultDisplayFieldSlug` and optional `clearDefaultDisplayField`.
- **Field API** — `GET`/`POST`/`PATCH` field responses include a `config` object when present (same JSON as stored on `entity_fields.config`).
- **Form layouts** — Layout items may include optional `referenceLookup` (e.g. `entityId`, template key, display field slugs) for future reference-picker UX; validated by `FormLayoutJsonValidator`.

## Main endpoints (v1)

Schema:

- `POST /v1/entities`
- `GET /v1/entities`
- `GET /v1/entities/{entityId}`
- `PATCH /v1/entities/{entityId}`
- `DELETE /v1/entities/{entityId}`
- `POST /v1/entities/{entityId}/fields`
- `GET /v1/entities/{entityId}/fields`
- `GET /v1/entities/{entityId}/fields/{fieldId}`
- `PATCH /v1/entities/{entityId}/fields/{fieldId}`
- `DELETE /v1/entities/{entityId}/fields/{fieldId}`
- `POST /v1/entities/{baseEntityId}/extensions`
- `GET /v1/entities/{baseEntityId}/extensions`
- `DELETE /v1/entities/{baseEntityId}/extensions/{extensionEntityId}`

**Tenant extensions (data model):** Metadata lives in **`tenant_entity_extensions`** (tenant, base entity, name, slug). Each extension also has a matching row in **`entities`** with `base_entity_id` and `tenant_entity_extension_id`, so **`entity_fields`** / **`entity_records`** keep working unchanged. Field definitions are mirrored into **`tenant_entity_extension_fields`** whenever you create/update/delete fields on that extension entity.

- `POST /v1/entity-relationships`
- `GET /v1/entity-relationships`

Records:

- `POST /v1/tenants/{tenantId}/entities/{entityId}/records`
  - Optional header: `Idempotency-Key`
- `GET /v1/tenants/{tenantId}/entities/{entityId}/records`
- `POST /v1/tenants/{tenantId}/entities/{entityId}/records/query` — JSON body: optional nested `filter` (groups with `op` `and`/`or` and `children`, or leaf clauses with `field` slug, `op`, and `value`). Operators are type-specific (e.g. number/date: `eq`, `gte`, `between`, `in`; text: `contains`, `starts_with`). PII fields require `entity_builder:pii:read`. Omit `filter` to return all records (paginated). Same auth as record list.
- `GET /v1/tenants/{tenantId}/entities/{entityId}/records/lookup` — typeahead / picker search over `search_vector`
- `GET /v1/tenants/{tenantId}/entities/{entityId}/records/{recordId}`
- `PATCH /v1/tenants/{tenantId}/entities/{entityId}/records/{recordId}`
- `DELETE /v1/tenants/{tenantId}/entities/{entityId}/records/{recordId}`

Form layouts:

- `GET /v1/entities/{entityId}/form-layouts`
- `POST /v1/entities/{entityId}/form-layouts`
- `POST /v1/entities/{entityId}/form-layouts/from-template` — body `templateKey`, `name`, `isDefault` (classpath templates under `form-layout-library/`)
- `GET /v1/entities/{entityId}/form-layouts/{layoutId}`
- `PATCH /v1/entities/{entityId}/form-layouts/{layoutId}`
- `DELETE /v1/entities/{entityId}/form-layouts/{layoutId}`

Form layout templates (classpath):

- `GET /v1/form-layout-templates` — optional query `includeLayout=true` for full layout JSON per template

## E2E tests

End-to-end system tests in `src/test/java/com/erp/entitybuilder/e2e/` exercise each service against a real CockroachDB instance. **CockroachDB must be running** (see `cockroachdb/README.md`).

```bash
./gradlew :entity-builder:test --tests "com.erp.entitybuilder.e2e.*"

./gradlew :entity-builder:test --tests "com.erp.entitybuilder.e2e.TenantEntityExtensionsE2ETest"
```

If CockroachDB is not reachable, E2E tests are skipped.


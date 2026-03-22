# Hybrid core + entity-builder extensions (implementation summary)

This document records what was implemented for the **hybrid hardcoded core + entity-builder EAV extensions** pattern: core business state in domain services (e.g. loans-module), extensions in `entity_record_values`, metadata in `entities` / `entity_fields`, IAM unchanged. It complements [`EntityField_storage_contract.md`](EntityField_storage_contract.md) and [`hybrid_domain_extensions.md`](hybrid_domain_extensions.md).

Related plan (reference only; do not treat this file as the plan source of truth): Cursor plan *Hybrid core + dynamic fields* (revised).

---

## 1. Metadata contract

- **[`design/EntityField_storage_contract.md`](EntityField_storage_contract.md)** — `storage`, `coreBinding`, `readOnly`, and API behavior.
- **Code:** [`FieldStorage`](entity-builder/src/main/java/com/erp/entitybuilder/service/storage/FieldStorage.java) ([`FieldStorageTest`](entity-builder/src/test/java/com/erp/entitybuilder/service/storage/FieldStorageTest.java)).
- **Docs:** [`entity-builder/README.md`](../entity-builder/README.md) section **Hybrid domain core + EAV extensions**.

---

## 2. Entity-builder record behavior

- **[`RecordsService`](entity-builder/src/main/java/com/erp/entitybuilder/service/RecordsService.java):**
  - Rejects any **`CORE_DOMAIN`** slugs in create/update record payloads.
  - **Required** validation applies only to **EAV** fields.
  - Persists values only for **EAV** fields; response **`values`** omit core slugs.
  - Search vector skips core fields; idempotency hash uses **EAV-only** values.
  - Display label falls back to **`external_id`**, then record id.
- **[`RecordFilterValidator`](entity-builder/src/main/java/com/erp/entitybuilder/service/query/RecordFilterValidator.java):** cannot filter on `CORE_DOMAIN` fields.
- **HTTP:**
  - `GET .../records/by-external-id/{externalId}` — [`RecordsController`](entity-builder/src/main/java/com/erp/entitybuilder/web/v1/RecordsController.java), [`getRecordByExternalId`](entity-builder/src/main/java/com/erp/entitybuilder/service/RecordsService.java).
  - `GET /v1/entities/by-slug/{slug}` — [`EntitiesController`](entity-builder/src/main/java/com/erp/entitybuilder/web/v1/EntitiesController.java), [`getEntityBySlug`](entity-builder/src/main/java/com/erp/entitybuilder/service/EntitySchemaService.java).
- **Extension row lifecycle:** **lazy** by default (create when first saving EAV data); optional eager creation can be added via domain service or jobs (documented in storage contract).

---

## 3. Classpath manifest sync

- **Resources:** [`entity-builder/src/main/resources/system-entity-catalog/index.json`](../entity-builder/src/main/resources/system-entity-catalog/index.json), [`loan_application.json`](../entity-builder/src/main/resources/system-entity-catalog/loan_application.json).
- **Service / API:** [`SystemCatalogSyncService`](entity-builder/src/main/java/com/erp/entitybuilder/service/catalog/SystemCatalogSyncService.java), [`SystemCatalogController`](entity-builder/src/main/java/com/erp/entitybuilder/web/v1/SystemCatalogController.java) — `POST /v1/tenants/{tenantId}/catalog/sync?manifestKey=...` (requires `entity_builder:schema:write`, tenant-scoped or cross-tenant entity-builder admin).
- **Categories:** **`lending`** added to [`EntityCategoryKeys.ALLOWED`](../entity-builder/src/main/java/com/erp/entitybuilder/domain/EntityCategoryKeys.java).

---

## 4. Loans module (core SoT)

- **Gradle:** [`loans-module/`](../loans-module/) module, included in [`settings.gradle`](../settings.gradle).
- **Schema:** Flyway [`V1__loan_applications.sql`](../loans-module/src/main/resources/db/migration/V1__loan_applications.sql) (`flyway_lending_schema_history`).
- **API:** `POST/GET/PATCH` [`/v1/tenants/{tenantId}/loans`](../loans-module/src/main/java/com/erp/loansmodule/web/v1/LoanApplicationsController.java) (default port **8083**, JWT aligned with IAM).
- **Overview:** [`loans-module/README.md`](../loans-module/README.md).

---

## 5. IAM and API gateway

- **Permissions:** [`iam/.../V14__lending_permissions.sql`](../iam/src/main/resources/db/migration/V14__lending_permissions.sql) — `lending:loans:read`, `lending:loans:write`. Seeded superadmin (bootstrap) receives all permissions including these.
- **Gateway:** [`api-gateway/application.yml`](../api-gateway/src/main/resources/application.yml) — `/v1/tenants/*/loans/**` → loans-module (`LOANS_MODULE_URL`); `/v1/tenants/*/catalog/**` → entity-builder.

---

## 6. Portal merge (demo)

- **API helpers:** [`erp-portal/src/api/hybridLoan.ts`](../erp-portal/src/api/hybridLoan.ts) — loans-module, catalog sync, entity-by-slug, record-by-external-id, **`loadMergedLoanView`**.
- **UI:** [`LoanHybridDemoPage`](../erp-portal/src/pages/LoanHybridDemoPage.tsx), route **`/loans-module/hybrid-demo`**, link from [`HomePage`](../erp-portal/src/pages/HomePage.tsx).

---

## 7. Optional IAM profile pattern (no IAM migration)

- **[`design/hybrid_domain_extensions.md`](hybrid_domain_extensions.md)** — `external_id = tenant_users.id` for member-scoped extensions without changing IAM tables.

---

## 8. Local verification

1. Apply IAM **V14** and run **loans-module** Flyway on the shared database.
2. Run **loans-module** (8083), **entity-builder** (8081), **gateway** (8000).
3. Ensure the user has **`lending:loans:*`** and **`entity_builder:schema:write`** (or use superadmin).
4. In the portal demo: **Sync catalog** → **Create + merge**; optional **Branch notes** creates the EAV row.

**Note:** On some Windows environments, `:entity-builder:test` may fail with a file lock on `build/test-results`; `:entity-builder:compileJava` / `compileTestJava` should still validate sources. Re-run tests after releasing the lock.

---

## 9. Principles (from the revised plan)

| Concern | System of record |
|--------|-------------------|
| Core columns | Domain module table + REST API (e.g. `loan_applications`) |
| Extension values | `entity_record_values` only (linked by `entity_records.external_id` = core row id) |
| Field definitions, layouts | `EntityDefinition`, `EntityField`, `form_layouts` |
| Identity / RBAC | IAM (unchanged for this pattern) |
| Primary SPA | erp-portal |

**Avoid:** duplicating core facts in EAV; implementing loan workflow inside entity-builder.

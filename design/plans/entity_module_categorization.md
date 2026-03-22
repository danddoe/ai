# Entity flat module categorization

**Status:** Implemented in entity-builder (nullable `entities.category_key`, API on `/v1/entities`).

## Decision

Use **flat categorization only**: each dynamic entity has at most one optional **`category_key`** (nullable string). No `entity_categories` table, no path column, no multi-category links in v1.

Hierarchy (e.g. Finance → Accounts Payable) stays in **portal nav**, **frontend/i18n**, or static maps — not on the `entities` row.

## Goal

Classify entities into ERP **module buckets** (e.g. `general_ledger`, `accounts_payable`, `accounts_receivable`) for list filtering, documentation parity with IAM nav, and future search facets — with minimal schema and queries.

## Alignment with IAM

Reuse the **same snake_case vocabulary** as IAM [`PortalNavigationItem.category_key`](../../iam/src/main/java/com/erp/iam/domain/PortalNavigationItem.java) (see [`iam/README.md`](../../iam/README.md) / Portal navigation docs) so shell grouping and entity-builder lists can stay consistent.

## Storage (entity-builder)

- **Table:** `entities`
- **Column:** `category_key VARCHAR(64) NULL`
- **Index:** `(tenant_id, category_key)` for filtered lists (partial index optional if most rows are NULL; a normal btree is fine for moderate volume).

**Validation:** application allow-list (enum or curated set in `EntitySchemaService`) so typos do not become permanent. Optionally document canonical keys in one place (short list in README or shared constants class). Null means “uncategorized.”

**Synthetic / extension-backed entities:** on create, **inherit** `category_key` from the base entity when applicable; allow PATCH to set or clear explicitly if product needs overrides.

## API (entity-builder)

| Piece | Change |
|--------|--------|
| [`EntityDtos.EntityDto`](../../entity-builder/src/main/java/com/erp/entitybuilder/web/v1/dto/EntityDtos.java) | Add nullable `categoryKey` |
| `CreateEntityRequest` / `UpdateEntityRequest` | Optional `categoryKey`; update may clear with explicit null or `clearCategoryKey` flag (match existing `clearDefaultDisplayField` pattern) |
| [`EntitiesController`](../../entity-builder/src/main/java/com/erp/entitybuilder/web/v1/EntitiesController.java) | `GET /v1/entities?categoryKey=` optional filter |
| [`EntitySchemaService`](../../entity-builder/src/main/java/com/erp/entitybuilder/service/EntitySchemaService.java) | Validate key; `listEntities(tenantId, Optional<String> categoryKey)` |
| [`EntityDefinitionRepository`](../../entity-builder/src/main/java/com/erp/entitybuilder/repository/EntityDefinitionRepository.java) | `findAllByTenantIdAndCategoryKeyOrderByNameAsc` (and existing list method when filter absent) |
| [`EntityDefinition`](../../entity-builder/src/main/java/com/erp/entitybuilder/domain/EntityDefinition.java) | Map column |

**Flyway:** new migration under `entity-builder/src/main/resources/db/migration/` (next version after current latest in that module).

## Optional follow-ups (not in v1 scope)

- **Global search:** denormalize `category_key` onto search documents if omnibox should group by module ([`GlobalSearchDocument`](../../entity-builder/src/main/java/com/erp/entitybuilder/domain/GlobalSearchDocument.java) has no field today).
- **IAM:** ensure nav seeds use the same keys you allow on entities (process/docs, not a hard FK).

---

## Appendix: deferred hierarchical / other shapes

If requirements later need tenant-editable trees or subtree queries, consider a separate **`entity_categories`** table (previous “Option B”), path strings (“Option C”), or multi-category links (“Option D”). The flat column can be migrated to FK or kept as redundant leaf key during transition.

## Implementation checklist (done)

1. **Flyway:** [`V8__entities_category_key.sql`](../../entity-builder/src/main/resources/db/migration/V8__entities_category_key.sql).
2. **Allow-list:** [`EntityCategoryKeys`](../../entity-builder/src/main/java/com/erp/entitybuilder/domain/EntityCategoryKeys.java).
3. **API:** `categoryKey` on entity DTOs; `GET /v1/entities?categoryKey=`; PATCH `categoryKey` / `clearCategoryKey`; extensions inherit base key on create.

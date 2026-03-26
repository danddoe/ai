# Entity status — how to use it

This document describes how **entity status** works in **entity-builder** today: configuration, provisioning, HTTP APIs, and how records expose workflow status. For the full data model and platform-tenant rules, see [`design/plans/entity_status_schema_f8c530c9.plan.md`](plans/entity_status_schema_f8c530c9.plan.md).

## Concepts (short)

| Concept | Meaning |
| -------- | -------- |
| **Native status tables** | `entity_status`, `entity_status_label`, `entity_status_transition` hold canonical definitions (codes, flags, transitions). |
| **Mirror entity** | A standard entity with slug **`entity_status`** (under the platform tenant) mirrors each native row as an **EAV record** so list/search and the portal behave like any other entity. |
| **`RecordScope`** | `STANDARD_RECORD` = platform catalog rows (stored under the platform tenant UUID, **visible to all tenants on read**). `TENANT_RECORD` = normal tenant-owned rows. |
| **`entity_records.entity_status_id`** | Optional FK from any **entity record** to **`entity_status.id`**. Populate this to attach a workflow status to a business record. The legacy string column **`entity_records.status`** (e.g. `ACTIVE`) is separate and unchanged. |
| **`entity_status_assignment`** | Per **tenant** + **scope** (`ENTITY_DEFINITION` or `ENTITY_FIELD`), which `entity_status.id` values are allowed when scoping **reference → `entity_status`** lookups and list/lookup filters. **`scope_id`** is the **`entities.id`** or **`entity_fields.id`** respectively. If **`assignedForEntityFieldId`** is passed on record APIs and that field has **at least one** assignment row, those ids win; else **`assignedForEntityId`** uses definition-scoped rows; if the chosen scope has **no** rows, there is **no** assignment filter. |
| **Catalog row shape** | Each **`entity_status`** row is **global** to its owning tenant (`tenant_id` + `code` unique). Usage is via **`entity_status_assignment`** only (native **`entity_status`** has no definition FK). |

Java constants for slugs: `EntityStatusCatalogConstants` → `entity_status`, `entity_status_parent`.

## Configuration

Set the **platform tenant UUID** (same as IAM bootstrap **“ai”** tenant) so provisioning and `STANDARD_RECORD` visibility work:

- **Property:** `entitybuilder.platform.tenant-id`
- **Env:** `ENTITY_BUILDER_PLATFORM_TENANT_ID`
- **Java:** `PlatformTenantProperties`

If this is **not** set, startup provisioning is skipped and the manual **ensure** endpoint returns **400** (“not configured”).

## Provisioning (idempotent)

The **`EntityStatusDynamicEntityProvisioner`** ensures:

1. Standard **`entity_status`** entity definition + fields + self-referential **parent** relationship (`entity_status_parent`).
2. Three seed statuses: **`ACTIVE`**, **`INACTIVE`**, **`DELETED`** (native rows + mirror `entity_records` with deterministic IDs).
3. Default **transitions** (e.g. ACTIVE ↔ INACTIVE, to DELETED).

**When it runs**

- **On application ready:** `EntityStatusStartupProvisioner` calls `ensureProvisioned()` if `entitybuilder.platform.tenant-id` is set (uses bootstrap security context).
- **On demand (HTTP):** see below.

**Manual ensure (operators)**

```http
POST /v1/tenants/{platformTenantId}/platform/entity-status/ensure
```

- **`platformTenantId`** must equal `entitybuilder.platform.tenant-id`.
- **Auth:** `entity_builder:schema:write` and JWT tenant must match the path tenant (`@entityBuilderSecurity.isTenant`).

**Status codes:** **200** OK; **400** if platform tenant not configured; **403** if path tenant ≠ configured platform tenant.

## Discovering status definitions (typical tenant flow)

1. Resolve the mirror entity (works from **any** tenant subject to schema read rules — the platform **`entity_status`** definition is surfaced in entity lists):

   ```http
   GET /v1/entities/by-slug/entity_status
   ```

2. List **mirror records** (each row corresponds to one native status; **`externalId`** on the mirror matches the **code**, e.g. `ACTIVE`):

   ```http
   GET /v1/tenants/{tenantId}/entities/{entityStatusEntityId}/records?page=1&pageSize=50
   ```

   Optional **assignment filters** (reference UIs pass the **host** entity definition id; field-level overrides use the reference field id):

   ```http
   GET /v1/tenants/{tenantId}/entities/{entityStatusEntityId}/records?assignedForEntityId={hostEntityDefinitionId}&page=1&pageSize=50
   ```

   ```http
   GET /v1/tenants/{tenantId}/entities/{entityStatusEntityId}/records?assignedForEntityId={hostEntityDefinitionId}&assignedForEntityFieldId={referenceFieldId}&page=1&pageSize=50
   ```

   **Resolution:** if **`assignedForEntityFieldId`** is set and there is **at least one** row with `(assignment_scope = ENTITY_FIELD, scope_id = field id)`, use those status ids. Otherwise, if **`assignedForEntityId`** is set and there is **at least one** row with `(assignment_scope = ENTITY_DEFINITION, scope_id = entity id)`, use those. If neither scope yields rows, there is **no** extra filter (same as an unfiltered list).

   The same query parameters are supported on **`GET .../records/lookup`**.

   Tenant **A** and tenant **B** both see the **`STANDARD_RECORD`** rows even though they are stored under the **platform** `tenant_id` (read visibility is scoped in the service layer).

Deterministic **`entity_status.id`** (and mirror **record id** for new inserts) uses a stable derivation from the platform tenant id and code (see `EntityStatusDynamicEntityProvisioner`: salt `platformTenantId + "|entity_status|" + code`).

## Localized labels (optional)

Per-status translations are stored in **`entity_status_label`**.

**List labels** (locale → text):

```http
GET /v1/entity-status/{entityStatusId}/labels
```

**Upsert or clear** (empty body or blank label deletes that locale):

```http
PUT /v1/entity-status/{entityStatusId}/labels/{locale}
Content-Type: application/json

{ "label": "Active" }
```

**Auth**

- **GET:** `@entityBuilderSecurity.canReadSchema()`
- **PUT:** `@entityBuilderSecurity.canWriteTenantSchema()` for tenant-owned statuses; **platform** `STANDARD_RECORD` labels require **full** schema write (`canWriteFullSchema` — platform catalog).

**Locale:** normalized server-side (e.g. `en`, `es`); max length **16**; label max **255** characters.

## Status assignments (definition scope and field scope)

Use this so a **reference** field targeting **`entity_status`** only offers a **subset** of statuses.

### Entity definition scope (`ENTITY_DEFINITION`)

**List current assignments**

```http
GET /v1/tenants/{tenantId}/entities/{entityDefinitionId}/entity-status-assignments
```

**List statuses the tenant may assign** (platform `STANDARD_RECORD` + tenant `TENANT_RECORD` that the principal can read)

```http
GET /v1/tenants/{tenantId}/entities/{entityDefinitionId}/entity-status-assignments/available
```

**Replace assignments** (full list; order preserved as `sort_order`)

```http
PUT /v1/tenants/{tenantId}/entities/{entityDefinitionId}/entity-status-assignments
Content-Type: application/json

{ "entityStatusIds": [ "uuid", "uuid" ] }
```

### Field scope (`ENTITY_FIELD`)

Nested under the owning entity so the server can verify the field belongs to **`entityId`**:

```http
GET /v1/entities/{entityId}/fields/{fieldId}/entity-status-assignments
GET /v1/entities/{entityId}/fields/{fieldId}/entity-status-assignments/available
PUT /v1/entities/{entityId}/fields/{fieldId}/entity-status-assignments
```

JWT **tenant** is taken from the principal (same pattern as **`GET /v1/entities/{entityId}/fields`**).

**Auth:** schema read for GET; **`entity_builder:schema:tenant_write`** for PUT on field routes. Mutations use **`canMutateEntitySchema`** on the **parent entity** (same as definition-scoped PUT).

**Portal:** **Form layouts** (`#status-assignments`) and **Entity settings** manage **definition** scope. **Edit field** (reference → **`entity_status`**) includes **field**-scoped assignments; when present, runtime lookups pass **`assignedForEntityFieldId`** so that list wins over entity-level rows. The **records** list page continues to use **entity-level** assignment filtering only (`assignedForEntityId`).

## Record API: reading workflow status

Record responses include:

| Field | Description |
| ----- | ----------- |
| **`entityStatusId`** | FK to `entity_status.id`, or `null`. |
| **`entityStatusDisplayLabel`** | Resolved for the current request locale: label from **`entity_status_label`** if present, else **`entity_status.label`**, using `Accept-Language` / `RequestLocaleResolver` when available. |

So clients can show a stable **id** for writes and a friendly **label** for UI.

## Assigning `entity_status` to business records (current limitation)

- The persistence column **`entity_records.entity_status_id`** exists and is returned on **GET** record(s).
- **Standard** create/update record payloads (`CreateRecordRequest` / `UpdateRecordRequest`) **do not** yet expose `entityStatusId`; **`RecordsService` create/update** does not set it for normal API traffic.
- Today the column is populated for the **provisioned mirror rows** of the **`entity_status`** entity (each mirror record’s `entity_status_id` points at its native status row).

**Implication:** wiring workflow status onto **arbitrary** entities requires either a **future API extension** (patch/create field for `entityStatusId` with validation against allowed statuses) or an **internal/backend** path. Until then, treat **`entityStatusId`** on record JSON as **read mostly** for non-catalog entities.

## Permissions cheat sheet

| Action | Typical authority |
| ------ | ----------------- |
| Ensure platform catalog | `entity_builder:schema:write` + platform tenant in path |
| Read schema / list `entity_status` entity | `entity_builder:schema:read` (and records read for listing mirror rows) |
| Edit **tenant** status labels | `entity_builder:schema:write` (tenant schema) |
| Edit **platform** `STANDARD_RECORD` labels | Full platform schema write (see `EntityStatusLabelService`) |

## Troubleshooting

- **No `entity_status` in entity list / 404 on by-slug:** platform tenant id missing or provisioning failed on startup — check logs (`EntityStatusStartupProvisioner`) and configuration; call **ensure** with the correct tenant and token.
- **Empty labels map:** no rows in `entity_status_label` yet — UI falls back to native **`label`** column.
- **E2E reference:** `EntityStatusProvisionE2ETest` exercises **ensure**, cross-tenant visibility of mirror records, and **`assignedForEntityId`** filtering. `EntityStatusAssignmentE2ETest` covers definition- and field-scoped assignment APIs, SQL for `assignment_scope` / `scope_id`, and **`assignedForEntityFieldId`** list filtering with fallback to entity scope.

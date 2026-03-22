# EntityField `config` — hybrid core + EAV storage

This contract lets one **EntityDefinition** describe both:

- **Core columns** owned by a domain module (e.g. `loan_application` table + lending API).
- **Extension attributes** stored in **`entity_record_values`** (EAV).

The Form Builder and a future **runtime form renderer** read the same `entity_fields` rows. Values for **`CORE_DOMAIN`** fields are **not** persisted by entity-builder; the portal or BFF merges core DTOs with record payloads.

## Keys (JSON in `entity_fields.config`)

| Key | Type | Description |
|-----|------|-------------|
| `storage` | string | `CORE_DOMAIN` — metadata only; values live in the domain service. `EAV_EXTENSION` (default if omitted) — values in `entity_record_values`. |
| `coreBinding` | object | Optional documentation for drift tests / tooling: e.g. `{ "service": "lending", "column": "requested_amount" }`. Not enforced by entity-builder persistence. |
| `readOnly` | boolean | Hint for UI: field should not be editable in extension-only forms (core is still edited via domain API). |

## API behavior (entity-builder)

- **Create/update record:** Clients must **not** send values for `CORE_DOMAIN` slugs; requests are rejected with `400` and a clear error.
- **Required:** `required: true` on `CORE_DOMAIN` fields applies to the **domain API**, not to entity-builder record APIs. Only **EAV_EXTENSION** fields are validated as required on create/update record.
- **Response `values` map:** Only **EAV_EXTENSION** slugs appear (core keys are omitted so the payload is extension-only).
- **Record query filters:** Filtering on `CORE_DOMAIN` slugs is rejected (no values in EAV).
- **Search vector:** Only EAV-backed values contribute (unchanged; core has no stored values).

## Runtime renderer (erp-portal)

1. Load entity + fields + layout from entity-builder.
2. Load core document from the domain module API (e.g. lending).
3. Load extension record by `GET .../records/by-external-id/{coreRowUuid}` when an extension row exists.
4. Merge maps: core properties + `record.values` (no overlapping slugs).

## Sync

Classpath manifests under `entity-builder` (`system-entity-catalog/`) define default `loan_application` (and future) shapes. **POST** ` /v1/tenants/{tenantId}/catalog/sync` upserts entities and fields idempotently (requires `entity_builder:schema:write`).

# Portal UI creation wizard

The SPA route [`/ui/create`](../erp-portal/src/pages/CreateUiWizardPage.tsx) walks through **template → entity → navigation metadata → review**, then:

1. Optionally patches the entity **default form layout** to **wizard** runtime (multi-step template only).
2. Calls IAM **`POST /v1/navigation/items`** to insert a row into `portal_navigation_items`.

## IAM: tenant vs global

| `tenant_id` | Visibility |
|-------------|------------|
| `NULL` | **Global**: any user who passes `required_permissions` / `required_roles` sees the item. |
| Non-null | **Tenant-scoped**: only users whose JWT `tenant_id` matches the row. |

- **Create (tenant)**: JWT must include `portal:navigation:write` **or** `entity_builder:schema:write`. `tenant_id` is taken from the token.
- **Create (global)**: `iam:security:admin` or `iam:superadmin`. Parent must be a **global** row (`tenant_id` null).
- **Promote to global**: `PATCH` with `promoteToGlobal: true` (same admin permissions).

`route_path` is allowlisted to `/entities/{uuid}/records` and `/entities/{uuid}/records/new` ([`PortalNavigationAdminService`](../iam/src/main/java/com/erp/iam/service/PortalNavigationAdminService.java)).

## Permission seed

- `portal:navigation:write` is inserted in Flyway [`V15__portal_navigation_tenant_scope.sql`](../iam/src/main/resources/db/migration/V15__portal_navigation_tenant_scope.sql).
- Optional: [`V16__grant_portal_nav_from_schema_write.sql`](../iam/src/main/resources/db/migration/V16__grant_portal_nav_from_schema_write.sql) grants it to every tenant role that already has `entity_builder:schema:write`.

## Templates (presets)

Defined in [`erp-portal/src/ui/uiTemplates.ts`](../erp-portal/src/ui/uiTemplates.ts). Nav `required_permissions` are set from template (form landing adds `entity_builder:records:write`).

See also runtime routes in [`Entity_Form_Builder_UI.md`](Entity_Form_Builder_UI.md) (Runtime record entry).

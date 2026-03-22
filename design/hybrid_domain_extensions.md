# Hybrid extensions: optional IAM profile (no IAM schema change)

When you need **tenant-scoped custom attributes on members** but want to **leave IAM tables unchanged**, reuse the same pattern as loans-module:

1. **Core** `tenant_users` / `users` rows stay authoritative in IAM (existing APIs).
2. In entity-builder, define an entity (e.g. `tenant_member_profile`) or reuse a catalog manifest with `CORE_DOMAIN` metadata fields mirroring IAM columns you display (optional) plus `EAV_EXTENSION` fields for custom data.
3. Create an **`entity_records`** row with **`external_id` = `tenant_users.id`** (the membership row UUID from IAM), not the global `users.id`, so extensions are **per tenant membership**.
4. **Lazy vs eager:** create the extension record on first custom save, or eagerly when IAM adds a member (requires IAM → entity-builder call or async job—same options as catalog sync).

This avoids `tenant_users.custom_metadata` while keeping omnibox/search on extensions inside entity-builder’s EAV pipeline.

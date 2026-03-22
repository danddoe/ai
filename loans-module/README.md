# lending

Spring Boot service (default port **8083**) for **loan application** core data (`loan_applications` table). Custom / tenant fields stay in **entity-builder** EAV, linked by `entity_records.external_id` = loan id.

- **Flyway:** `flyway_lending_schema_history` on the shared database (same pattern as IAM / entity-builder).
- **Gateway:** `/v1/tenants/{tenantId}/loans/**` → lending (see `api-gateway`).
- **Permissions (IAM):** `lending:loans:read`, `lending:loans:write` (seeded in `V14__lending_permissions.sql`). Superadmin / security admin may also call APIs via `LendingSecurity`.
- **Hybrid demo in portal:** [erp-portal `/lending/hybrid-demo`](../erp-portal) merges this API with entity-builder extension records.

JWT issuer/audience must match IAM (`JWT_SECRET`, `JWT_ISSUER`, `JWT_AUDIENCE`).

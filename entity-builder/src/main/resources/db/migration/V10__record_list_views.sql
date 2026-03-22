-- Persisted record list view definitions (columns, presentation) per tenant + entity.

CREATE TABLE IF NOT EXISTS record_list_views (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    entity_id UUID NOT NULL REFERENCES entities(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    definition JSONB NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT false,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(tenant_id, entity_id, name)
);

CREATE INDEX IF NOT EXISTS idx_record_list_views_tenant_entity ON record_list_views(tenant_id, entity_id);

-- At most one default per (tenant, entity); same STORED-slot pattern as form_layouts (Cockroach).
ALTER TABLE record_list_views
    ADD COLUMN IF NOT EXISTS default_uniqueness_slot INT8
        AS (CASE WHEN is_default THEN 0::INT8 ELSE NULL::INT8 END) STORED;

CREATE UNIQUE INDEX IF NOT EXISTS ux_record_list_views_default_per_entity
    ON record_list_views (tenant_id, entity_id, default_uniqueness_slot);

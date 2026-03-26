-- Global entity_status catalog (no per-row entity_definition_id); per-entity usage via assignment join.

CREATE TABLE IF NOT EXISTS entity_status_assignment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    entity_definition_id UUID NOT NULL REFERENCES entities(id) ON DELETE CASCADE,
    entity_status_id UUID NOT NULL REFERENCES entity_status(id) ON DELETE CASCADE,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT entity_status_assignment_uq UNIQUE (tenant_id, entity_definition_id, entity_status_id)
);

CREATE INDEX IF NOT EXISTS idx_entity_status_assignment_tenant_entity
    ON entity_status_assignment(tenant_id, entity_definition_id);

DROP INDEX IF EXISTS entity_status_tenant_def_code_uq;
DROP INDEX IF EXISTS entity_status_tenant_null_def_code_uq;
DROP INDEX IF EXISTS idx_entity_status_entity_definition_id;

ALTER TABLE entity_status DROP COLUMN IF EXISTS entity_definition_id;

CREATE UNIQUE INDEX IF NOT EXISTS entity_status_tenant_code_uq ON entity_status (tenant_id, code);

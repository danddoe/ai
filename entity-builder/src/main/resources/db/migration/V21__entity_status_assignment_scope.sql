-- Polymorphic assignment scope: entity definition or entity field (single table).

ALTER TABLE entity_status_assignment
    ADD COLUMN IF NOT EXISTS assignment_scope VARCHAR(32) NOT NULL DEFAULT 'ENTITY_DEFINITION';

ALTER TABLE entity_status_assignment
    ADD COLUMN IF NOT EXISTS scope_id UUID;

UPDATE entity_status_assignment SET scope_id = entity_definition_id WHERE scope_id IS NULL;

ALTER TABLE entity_status_assignment ALTER COLUMN scope_id SET NOT NULL;

ALTER TABLE entity_status_assignment DROP CONSTRAINT IF EXISTS entity_status_assignment_entity_definition_id_fkey;

ALTER TABLE entity_status_assignment DROP COLUMN IF EXISTS entity_definition_id;

ALTER TABLE entity_status_assignment DROP CONSTRAINT IF EXISTS entity_status_assignment_uq;

ALTER TABLE entity_status_assignment
    ADD CONSTRAINT entity_status_assignment_uq UNIQUE (tenant_id, assignment_scope, scope_id, entity_status_id);

DROP INDEX IF EXISTS idx_entity_status_assignment_tenant_entity;

CREATE INDEX IF NOT EXISTS idx_entity_status_assignment_tenant_scope
    ON entity_status_assignment (tenant_id, assignment_scope, scope_id);

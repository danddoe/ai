-- Standard vs tenant-owned schema definitions (entities and relationships).
ALTER TABLE entities ADD COLUMN IF NOT EXISTS definition_scope VARCHAR(32) NOT NULL DEFAULT 'TENANT_OBJECT';
ALTER TABLE entity_relationships ADD COLUMN IF NOT EXISTS definition_scope VARCHAR(32) NOT NULL DEFAULT 'TENANT_OBJECT';

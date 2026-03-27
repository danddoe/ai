-- Soft-archive support: inactive fields stay in the schema for historical data but are excluded from writes and layout/list validation.

ALTER TABLE entity_fields ADD COLUMN IF NOT EXISTS status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_entity_fields_entity_status ON entity_fields (entity_id, status);

ALTER TABLE entity_fields ADD COLUMN IF NOT EXISTS sort_order INT NOT NULL DEFAULT 0;
ALTER TABLE entity_fields ADD COLUMN IF NOT EXISTS label_override VARCHAR(255) NULL;

ALTER TABLE tenant_entity_extension_fields ADD COLUMN IF NOT EXISTS sort_order INT NOT NULL DEFAULT 0;
ALTER TABLE tenant_entity_extension_fields ADD COLUMN IF NOT EXISTS label_override VARCHAR(255) NULL;

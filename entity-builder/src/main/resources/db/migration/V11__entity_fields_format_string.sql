ALTER TABLE entity_fields ADD COLUMN IF NOT EXISTS format_string VARCHAR(500) NULL;
ALTER TABLE tenant_entity_extension_fields ADD COLUMN IF NOT EXISTS format_string VARCHAR(500) NULL;

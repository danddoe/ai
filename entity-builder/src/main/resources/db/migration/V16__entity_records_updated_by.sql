ALTER TABLE entity_records
    ADD COLUMN IF NOT EXISTS updated_by UUID;

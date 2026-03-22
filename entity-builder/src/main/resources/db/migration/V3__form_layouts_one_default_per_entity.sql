-- At most one default layout per (tenant, entity). Application code also clears other defaults; this guards races.
-- CockroachDB does not support partial unique indexes (#9683) or unique indexes on arbitrary scalar expressions (#9682).
-- Use a STORED computed column + unique index on plain columns (see computed-columns docs).
DROP INDEX IF EXISTS ux_form_layouts_default_per_entity;

ALTER TABLE form_layouts
    ADD COLUMN IF NOT EXISTS default_uniqueness_slot INT8
        AS (CASE WHEN is_default THEN 0::INT8 ELSE NULL::INT8 END) STORED;

CREATE UNIQUE INDEX IF NOT EXISTS ux_form_layouts_default_per_entity
    ON form_layouts (tenant_id, entity_id, default_uniqueness_slot);

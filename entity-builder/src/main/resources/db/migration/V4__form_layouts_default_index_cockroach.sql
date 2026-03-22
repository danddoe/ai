-- Legacy installs: replace partial or expression-based unique index with STORED column + plain unique index.
DROP INDEX IF EXISTS ux_form_layouts_default_per_entity;

ALTER TABLE form_layouts
    ADD COLUMN IF NOT EXISTS default_uniqueness_slot INT8
        AS (CASE WHEN is_default THEN 0::INT8 ELSE NULL::INT8 END) STORED;

CREATE UNIQUE INDEX IF NOT EXISTS ux_form_layouts_default_per_entity
    ON form_layouts (tenant_id, entity_id, default_uniqueness_slot);

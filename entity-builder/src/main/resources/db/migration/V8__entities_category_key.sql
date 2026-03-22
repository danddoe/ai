-- Flat module bucket per entity (aligned with IAM portal_navigation_items.category_key vocabulary).

ALTER TABLE entities
    ADD COLUMN IF NOT EXISTS category_key VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_entities_tenant_category_key ON entities (tenant_id, category_key);

-- Add stable relationship slug for API lookups
ALTER TABLE entity_relationships ADD COLUMN IF NOT EXISTS slug VARCHAR(100);
CREATE INDEX IF NOT EXISTS idx_entity_relationships_tenant_slug ON entity_relationships(tenant_id, slug);
-- Relationship slug must be unique per tenant
-- Note: Cockroach allows adding unique constraint without full rewrite only in some cases.
-- We'll enforce uniqueness in application logic for v1.


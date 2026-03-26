-- Tenant-scoped schema changes (own entities, extensions, fields) without mutating platform catalog (STANDARD_OBJECT) entities.
INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'entity_builder:schema:tenant_write', 'Create and update tenant-owned entity schema; cannot modify platform catalog entities')
ON CONFLICT (code) DO NOTHING;

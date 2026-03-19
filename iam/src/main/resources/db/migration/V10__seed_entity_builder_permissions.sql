-- Seed entity-builder module permissions
INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'entity_builder:schema:read', 'View entity definitions, fields, relationships, and form layouts'),
    (gen_random_uuid(), 'entity_builder:schema:write', 'Create and update entity definitions, fields, relationships, and form layouts'),
    (gen_random_uuid(), 'entity_builder:records:read', 'View entity records'),
    (gen_random_uuid(), 'entity_builder:records:write', 'Create and update entity records'),
    (gen_random_uuid(), 'entity_builder:pii:read', 'View decrypted PII values in records'),
    (gen_random_uuid(), 'entity_builder:security:admin', 'Cross-tenant entity-builder administration'),
    (gen_random_uuid(), 'entity_builder:tenants:admin', 'Cross-tenant entity-builder administration')
ON CONFLICT (code) DO NOTHING;

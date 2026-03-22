-- Reproducible UUIDs for hierarchical seed. Parent and child must be separate statements:
-- CockroachDB can reject a single multi-row INSERT when child FK references parent in same statement.
INSERT INTO portal_navigation_items (
    id, parent_id, sort_order, route_path, label, description, type, icon,
    search_keywords, required_permissions, required_roles, is_active
) VALUES (
    'a0000001-0000-4000-8000-000000000001',
    NULL,
    0,
    NULL,
    'Workspace',
    'ERP modules',
    'section',
    NULL,
    '["workspace","erp","home"]'::jsonb,
    '[]'::jsonb,
    '[]'::jsonb,
    true
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO portal_navigation_items (
    id, parent_id, sort_order, route_path, label, description, type, icon,
    search_keywords, required_permissions, required_roles, is_active
) VALUES (
    'a0000001-0000-4000-8000-000000000002',
    'a0000001-0000-4000-8000-000000000001',
    0,
    '/entities',
    'Entities',
    'Entity definitions, fields, and form layouts',
    'internal',
    'layers',
    '["entities","schema","fields","forms","layouts","builder"]'::jsonb,
    '["entity_builder:schema:read"]'::jsonb,
    '[]'::jsonb,
    true
)
ON CONFLICT (id) DO NOTHING;

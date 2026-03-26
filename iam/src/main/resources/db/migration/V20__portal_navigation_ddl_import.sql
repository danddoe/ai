-- DDL import page under Workspace (Entity Builder); sibling of Entities.
INSERT INTO portal_navigation_items (
    id, parent_id, sort_order, route_path, label, description, type, icon,
    search_keywords, required_permissions, required_roles, is_active, category_key, tenant_id
) VALUES (
    'a0000001-0000-4000-8000-000000000003',
    'a0000001-0000-4000-8000-000000000001',
    1,
    '/entities/ddl-import',
    'Import from DDL',
    'Parse CREATE TABLE DDL into entities and fields',
    'internal',
    'database',
    '["ddl","sql","schema","import","create table"]'::jsonb,
    '["entity_builder:schema:read"]'::jsonb,
    '[]'::jsonb,
    true,
    'entity_builder',
    NULL
)
ON CONFLICT (id) DO NOTHING;

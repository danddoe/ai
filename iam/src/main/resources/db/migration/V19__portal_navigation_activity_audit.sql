-- Hub for entity record audit viewing; searchable in omnibox via keywords.
INSERT INTO portal_navigation_items (
    id, parent_id, sort_order, route_path, label, description, type, icon,
    search_keywords, required_permissions, required_roles, is_active, category_key, tenant_id
) VALUES (
    'a0000001-0000-4000-8000-000000000003',
    'a0000001-0000-4000-8000-000000000001',
    1,
    '/audit',
    'Activity & audit',
    'View change history across entity records',
    'internal',
    'file-text',
    '["audit","history","activity","changes","log","trail","record"]'::jsonb,
    '["entity_builder:records:read"]'::jsonb,
    '[]'::jsonb,
    true,
    'entity_builder',
    NULL
)
ON CONFLICT (id) DO NOTHING;

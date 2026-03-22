-- Module / product-area bucket for portal shell (e.g. entity_builder, accounting, accounts_payable).
-- Hierarchy remains parent_id + children; category_key supports grouped nav and search facets.
ALTER TABLE portal_navigation_items ADD COLUMN IF NOT EXISTS category_key VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_portal_nav_category ON portal_navigation_items(category_key);

UPDATE portal_navigation_items
SET category_key = 'entity_builder'
WHERE id IN (
    'a0000001-0000-4000-8000-000000000001',
    'a0000001-0000-4000-8000-000000000002'
);

-- Example finance area (placeholder route until SPA module exists)
-- Parent/child split: same FK visibility issue as V12 on CockroachDB.
INSERT INTO portal_navigation_items (
    id, parent_id, sort_order, route_path, label, description, type, icon,
    search_keywords, required_permissions, required_roles, is_active, category_key
) VALUES (
    'a0000001-0000-4000-8000-000000000010',
    NULL,
    1,
    NULL,
    'Accounting',
    'Financial operations',
    'section',
    'calculator',
    '["accounting","finance","gl","ledger"]'::jsonb,
    '[]'::jsonb,
    '[]'::jsonb,
    true,
    'accounting'
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO portal_navigation_items (
    id, parent_id, sort_order, route_path, label, description, type, icon,
    search_keywords, required_permissions, required_roles, is_active, category_key
) VALUES (
    'a0000001-0000-4000-8000-000000000011',
    'a0000001-0000-4000-8000-000000000010',
    0,
    '/accounts-payable',
    'Accounts payable',
    'Bills and vendor payments (route placeholder)',
    'internal',
    'wallet',
    '["ap","payables","vendors","bills"]'::jsonb,
    '[]'::jsonb,
    '[]'::jsonb,
    true,
    'accounts_payable'
)
ON CONFLICT (id) DO NOTHING;

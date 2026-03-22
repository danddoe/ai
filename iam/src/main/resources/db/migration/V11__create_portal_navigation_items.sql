-- Global SPA navigation entries (single portal to all ERP modules). Filtered server-side by JWT permissions/roles.
CREATE TABLE IF NOT EXISTS portal_navigation_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id UUID REFERENCES portal_navigation_items(id) ON DELETE CASCADE,
    sort_order INT NOT NULL DEFAULT 0,
    route_path VARCHAR(512),
    label VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    type VARCHAR(32) NOT NULL DEFAULT 'internal',
    icon VARCHAR(128),
    search_keywords JSONB NOT NULL DEFAULT '[]'::jsonb,
    required_permissions JSONB NOT NULL DEFAULT '[]'::jsonb,
    required_roles JSONB NOT NULL DEFAULT '[]'::jsonb,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_portal_nav_type CHECK (type IN ('internal', 'external', 'section', 'divider')),
    CONSTRAINT chk_portal_nav_route CHECK (
        (route_path IS NOT NULL AND length(trim(route_path)) > 0)
        OR type IN ('section', 'divider')
    )
);

CREATE INDEX IF NOT EXISTS idx_portal_nav_parent ON portal_navigation_items(parent_id);
CREATE INDEX IF NOT EXISTS idx_portal_nav_active ON portal_navigation_items(is_active);

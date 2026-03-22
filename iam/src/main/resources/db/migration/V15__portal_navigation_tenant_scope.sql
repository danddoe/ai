-- Tenant-scoped nav rows: NULL tenant_id = visible to all (subject to RBAC); non-NULL = only that tenant's users.
ALTER TABLE portal_navigation_items
    ADD COLUMN IF NOT EXISTS tenant_id UUID REFERENCES tenants(id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_portal_nav_tenant_id ON portal_navigation_items(tenant_id);

INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'portal:navigation:write', 'Create and update tenant-scoped portal navigation items')
ON CONFLICT (code) DO NOTHING;

-- Seed default permissions for ERP modules
INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'iam:users:read', 'View users in tenant'),
    (gen_random_uuid(), 'iam:users:write', 'Create and update users'),
    (gen_random_uuid(), 'iam:roles:read', 'View roles'),
    (gen_random_uuid(), 'iam:roles:write', 'Create and update roles'),
    (gen_random_uuid(), 'iam:tenant_users:read', 'View tenant members'),
    (gen_random_uuid(), 'iam:tenant_users:write', 'Invite and manage tenant members')
ON CONFLICT (code) DO NOTHING;

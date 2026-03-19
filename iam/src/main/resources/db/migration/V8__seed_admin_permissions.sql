-- Seed elevated permissions for cross-tenant administration
INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'iam:tenants:admin', 'Create/update/delete tenants (cross-tenant admin)'),
    (gen_random_uuid(), 'iam:permissions:admin', 'Manage permission registry (global)'),
    (gen_random_uuid(), 'iam:security:admin', 'Cross-tenant IAM administration operations')
ON CONFLICT (code) DO NOTHING;


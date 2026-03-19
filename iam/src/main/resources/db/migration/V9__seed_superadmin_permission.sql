-- Seed SUPERADMIN "allow all" permission
INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'iam:superadmin', 'Allow all IAM operations (superadmin)')
ON CONFLICT (code) DO NOTHING;

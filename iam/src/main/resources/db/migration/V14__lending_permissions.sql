INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'lending:loans:read', 'View loan applications for the tenant'),
    (gen_random_uuid(), 'lending:loans:write', 'Create and update loan applications for the tenant')
ON CONFLICT (code) DO NOTHING;

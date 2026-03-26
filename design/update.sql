

    INSERT INTO users (id, email, password_hash, display_name, status)
VALUES (
    gen_random_uuid(),
    'superai@unknownerp.com',
    '$2a$10$YOUR_BCRYPT_HASH_HERE',
    'Super AI',
    'ACTIVE'
    )


    INSERT INTO tenant_users (id, tenant_id, user_id, status, joined_at)
    VALUES (
        gen_random_uuid(),
        'ce4b2f01-87c7-45da-87ae-273f0cca1709',
        'ab50d957-d3c4-45ea-a24b-93ce355a74aa',
        'ACTIVE',
        now()
        )


    INSERT INTO user_roles (id, tenant_id, user_id, role_id)
    SELECT gen_random_uuid(),
           ur.tenant_id,
           'ab50d957-d3c4-45ea-a24b-93ce355a74aa'::uuid,
        ur.role_id
    FROM user_roles ur
             JOIN users u ON u.id = ur.user_id AND lower(u.email) = lower('superadmin@ai.com')
    WHERE ur.tenant_id = 'ce4b2f01-87c7-45da-87ae-273f0cca1709'::uuid
    ON CONFLICT (tenant_id, user_id, role_id) DO NOTHING;


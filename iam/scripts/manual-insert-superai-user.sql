-- Manual dev helper: add superai@unknownerp.com with the same tenant + roles as superadmin@ai.com,
-- and the SAME password_hash as superadmin (so you log in with the superadmin password).
--
-- Prerequisites:
--   - User superadmin@ai.com already exists with tenant_users + user_roles.
--   - If your primary admin uses a different email, replace 'superadmin@ai.com' below.
--
-- Run (example):
--   cockroach sql --insecure --host=localhost --port=26257 --database=defaultdb -f iam/scripts/manual-insert-superai-user.sql
--
-- Safe to re-run: uses ON CONFLICT DO NOTHING.

BEGIN;

INSERT INTO users (id, email, password_hash, display_name, status)
SELECT gen_random_uuid(),
       'superai@unknownerp.com',
       u.password_hash,
       'Super AI',
       'ACTIVE'
FROM users u
WHERE lower(u.email) = lower('superadmin@ai.com')
  AND u.status = 'ACTIVE'
LIMIT 1
ON CONFLICT (email) DO NOTHING;

INSERT INTO tenant_users (id, tenant_id, user_id, status, joined_at)
SELECT gen_random_uuid(),
       tu.tenant_id,
       sa.id,
       'ACTIVE',
       now()
FROM users tmpl
JOIN tenant_users tu ON tu.user_id = tmpl.id AND tu.status = 'ACTIVE'
JOIN users sa ON lower(sa.email) = lower('superai@unknownerp.com')
WHERE lower(tmpl.email) = lower('superadmin@ai.com')
ON CONFLICT (tenant_id, user_id) DO NOTHING;

INSERT INTO user_roles (id, tenant_id, user_id, role_id)
SELECT gen_random_uuid(),
       ur.tenant_id,
       sa.id,
       ur.role_id
FROM user_roles ur
JOIN users tmpl ON tmpl.id = ur.user_id AND lower(tmpl.email) = lower('superadmin@ai.com')
JOIN users sa ON lower(sa.email) = lower('superai@unknownerp.com')
ON CONFLICT (tenant_id, user_id, role_id) DO NOTHING;

COMMIT;

-- Verify:
-- SELECT id, email, display_name, status FROM users WHERE lower(email) = lower('superai@unknownerp.com');
-- SELECT * FROM tenant_users WHERE user_id = (SELECT id FROM users WHERE lower(email) = lower('superai@unknownerp.com'));
-- SELECT * FROM user_roles WHERE user_id = (SELECT id FROM users WHERE lower(email) = lower('superai@unknownerp.com'));

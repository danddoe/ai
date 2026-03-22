-- Grant portal:navigation:write anywhere entity_builder:schema:write is already granted (same tenant role rows).
INSERT INTO role_permissions (id, tenant_id, role_id, permission_id)
SELECT gen_random_uuid(), rp.tenant_id, rp.role_id, pn.id
FROM role_permissions rp
JOIN permissions sw ON sw.id = rp.permission_id AND sw.code = 'entity_builder:schema:write'
JOIN permissions pn ON pn.code = 'portal:navigation:write'
WHERE NOT EXISTS (
    SELECT 1 FROM role_permissions x
    WHERE x.tenant_id = rp.tenant_id AND x.role_id = rp.role_id AND x.permission_id = pn.id
);

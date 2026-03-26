import { apiFetch } from '../api/client';
import { readApiError } from '../api/schemas';

/** Sample tenant + admin for local demos (tenant can extend catalog entities, not edit their definitions). */
export const SAMPLE_SUPER_AI_TENANT = {
  name: 'Super AI Tenant',
  slug: 'super-ai-tenant',
  adminEmail: 'superaitenantadmin@superaitenant.com',
  adminDisplayName: 'Super AI Tenant Admin',
  roleName: 'TENANT_SCHEMA_ADMIN',
} as const;

const PERMISSION_CODES_FOR_TENANT_ADMIN = [
  'entity_builder:schema:read',
  'entity_builder:schema:tenant_write',
  'entity_builder:records:read',
  'entity_builder:records:write',
  'iam:tenant_users:read',
  'iam:tenant_users:write',
  'iam:roles:read',
  'iam:roles:write',
  'portal:navigation:write',
] as const;

type Page<T> = { items: T[]; page: number; pageSize: number; total: number };

async function getJson<T>(path: string): Promise<T> {
  const res = await apiFetch(path);
  if (!res.ok) throw new Error(await readApiError(res));
  return (await res.json()) as T;
}

async function postJson<T>(path: string, body: unknown): Promise<T> {
  const res = await apiFetch(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(await readApiError(res));
  return (await res.json()) as T;
}

async function putJson(path: string, body: unknown): Promise<void> {
  const res = await apiFetch(path, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(await readApiError(res));
}

/** Resolve permission UUIDs by code (paginates IAM list). */
export async function resolvePermissionIds(codes: string[]): Promise<Map<string, string>> {
  const want = new Set(codes);
  const out = new Map<string, string>();
  let page = 1;
  const pageSize = 200;
  while (out.size < want.size) {
    const data = await getJson<Page<{ id: string; code: string }>>(
      `/v1/permissions?page=${page}&pageSize=${pageSize}`
    );
    for (const p of data.items) {
      if (want.has(p.code)) out.set(p.code, p.id);
    }
    if (data.items.length < pageSize || page * pageSize >= data.total) break;
    page += 1;
  }
  return out;
}

export type SampleTenantSeedResult = {
  tenantId: string;
  userId: string;
  roleId: string;
  message: string;
};

/**
 * Creates tenant {@link SAMPLE_SUPER_AI_TENANT}, global user, tenant role with tenant-schema permissions, membership, and role assignment.
 * Caller must hold IAM platform access (e.g. superadmin). Password must meet IAM minimum length (8+).
 */
export async function seedSampleSuperAiTenant(adminPassword: string): Promise<SampleTenantSeedResult> {
  const pwd = adminPassword.trim();
  if (pwd.length < 8) {
    throw new Error('Password must be at least 8 characters (IAM policy).');
  }

  const permMap = await resolvePermissionIds([...PERMISSION_CODES_FOR_TENANT_ADMIN]);
  for (const code of PERMISSION_CODES_FOR_TENANT_ADMIN) {
    if (!permMap.has(code)) {
      throw new Error(`Permission not found in IAM: ${code}. Apply Flyway through V22+ on IAM, then retry.`);
    }
  }

  const tenant = await postJson<{ id: string }>('/v1/tenants', {
    name: SAMPLE_SUPER_AI_TENANT.name,
    slug: SAMPLE_SUPER_AI_TENANT.slug,
    status: 'ACTIVE',
  });

  const user = await postJson<{ id: string }>('/v1/users', {
    email: SAMPLE_SUPER_AI_TENANT.adminEmail,
    displayName: SAMPLE_SUPER_AI_TENANT.adminDisplayName,
    password: pwd,
    status: 'ACTIVE',
  });

  const role = await postJson<{ id: string }>(`/v1/tenants/${tenant.id}/roles`, {
    name: SAMPLE_SUPER_AI_TENANT.roleName,
    description: 'Tenant admin: own schema and members; cannot edit platform catalog entities.',
    system: false,
  });

  const permissionIds = PERMISSION_CODES_FOR_TENANT_ADMIN.map((c) => permMap.get(c) as string);
  await putJson(`/v1/tenants/${tenant.id}/roles/${role.id}/permissions`, { permissionIds });

  await postJson<unknown>(`/v1/tenants/${tenant.id}/members`, {
    email: SAMPLE_SUPER_AI_TENANT.adminEmail,
    status: 'ACTIVE',
  });

  await putJson(`/v1/tenants/${tenant.id}/users/${user.id}/roles`, { roleIds: [role.id] });

  return {
    tenantId: tenant.id,
    userId: user.id,
    roleId: role.id,
    message: `Created tenant slug "${SAMPLE_SUPER_AI_TENANT.slug}" and user ${SAMPLE_SUPER_AI_TENANT.adminEmail}. Log out and log in with that tenant slug and email.`,
  };
}

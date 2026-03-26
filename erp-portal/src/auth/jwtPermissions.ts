const SCHEMA_READ = 'entity_builder:schema:read';
const SCHEMA_WRITE = 'entity_builder:schema:write';
const SCHEMA_TENANT_WRITE = 'entity_builder:schema:tenant_write';
const RECORDS_READ = 'entity_builder:records:read';
const RECORDS_WRITE = 'entity_builder:records:write';
const PII_READ = 'entity_builder:pii:read';
const PORTAL_NAV_WRITE = 'portal:navigation:write';
const IAM_SECURITY_ADMIN = 'iam:security:admin';
const IAM_SUPERADMIN = 'iam:superadmin';
const IAM_TENANTS_ADMIN = 'iam:tenants:admin';

/** Decode JWT payload (no signature verify) for UI-only hints; authorization remains server-side. */
export function parseJwtPayload(accessToken: string | null): Record<string, unknown> | null {
  if (!accessToken) return null;
  const parts = accessToken.split('.');
  if (parts.length !== 3) return null;
  try {
    const payloadJson = base64UrlDecode(parts[1]);
    return JSON.parse(payloadJson) as Record<string, unknown>;
  } catch {
    return null;
  }
}

export function parseJwtPermissions(accessToken: string | null): string[] {
  const payload = parseJwtPayload(accessToken);
  if (!payload) return [];
  const perms = payload.permissions;
  if (!Array.isArray(perms)) return [];
  return perms.filter((p): p is string => typeof p === 'string');
}

/** IAM access token claim `tenant_id` (UUID string). */
export function parseJwtTenantId(accessToken: string | null): string {
  const payload = parseJwtPayload(accessToken);
  if (!payload) return '';
  const tid = payload.tenant_id;
  return typeof tid === 'string' ? tid : '';
}

export function canSchemaRead(permissions: string[]): boolean {
  return (
    permissions.includes(SCHEMA_READ) ||
    permissions.includes(SCHEMA_WRITE) ||
    permissions.includes(SCHEMA_TENANT_WRITE)
  );
}

/** Create/edit tenant-owned entities, extensions, fields, layouts — not platform catalog entity definitions. */
export function canSchemaWrite(permissions: string[]): boolean {
  return permissions.includes(SCHEMA_WRITE) || permissions.includes(SCHEMA_TENANT_WRITE);
}

/** Catalog sync, DDL import apply, mutating STANDARD_OBJECT entity definitions/fields. */
export function canPlatformSchemaWrite(permissions: string[]): boolean {
  return permissions.includes(SCHEMA_WRITE);
}

export type DefinitionScopeHint = { definitionScope?: string };

/** UI guard: catalog entities require full {@link SCHEMA_WRITE}; tenant-owned allow tenant schema write. */
export function canMutateEntityDefinition(
  permissions: string[],
  entity: DefinitionScopeHint | null | undefined
): boolean {
  if (!entity) return false;
  if (entity.definitionScope === 'STANDARD_OBJECT') {
    return permissions.includes(SCHEMA_WRITE);
  }
  return canSchemaWrite(permissions);
}

export function canRecordsRead(permissions: string[]): boolean {
  return permissions.includes(RECORDS_READ);
}

export function canRecordsWrite(permissions: string[]): boolean {
  return permissions.includes(RECORDS_WRITE);
}

export function canPiiRead(permissions: string[]): boolean {
  return permissions.includes(PII_READ);
}

/** Tenant-scoped (or global with admin) portal nav item create; matches IAM nav write API. */
export function canCreatePortalNavItem(permissions: string[]): boolean {
  return (
    permissions.includes(PORTAL_NAV_WRITE) ||
    permissions.includes(SCHEMA_WRITE) ||
    permissions.includes(SCHEMA_TENANT_WRITE)
  );
}

export function canManageGlobalNavigation(permissions: string[]): boolean {
  return permissions.includes(IAM_SECURITY_ADMIN) || permissions.includes(IAM_SUPERADMIN);
}

/** Create sample tenant + tenant admin via IAM APIs (dev tooling; requires platform IAM access). */
export function canRunSampleTenantSeed(permissions: string[]): boolean {
  return (
    permissions.includes(IAM_TENANTS_ADMIN) ||
    permissions.includes(IAM_SECURITY_ADMIN) ||
    permissions.includes(IAM_SUPERADMIN)
  );
}

function base64UrlDecode(segment: string): string {
  const pad = '='.repeat((4 - (segment.length % 4)) % 4);
  const b64 = (segment + pad).replace(/-/g, '+').replace(/_/g, '/');
  const binary = atob(b64);
  try {
    const bytes = Uint8Array.from(binary, (c) => c.charCodeAt(0));
    return new TextDecoder().decode(bytes);
  } catch {
    return binary;
  }
}

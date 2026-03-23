import { apiFetch } from './client';
import { readApiError, type EntityFieldDto } from './schemas';
import { isCoreDomainField, readCoreBindingColumn } from '../utils/fieldStorage';

/** Master-data entities whose CORE_DOMAIN rows are owned by core-service (see system-entity-catalog). */
const HYBRID_SPECS: Record<
  string,
  { pathSegment: string; idJsonKey: string; createKeys: Set<string>; patchKeys: Set<string> }
> = {
  company: {
    pathSegment: 'companies',
    idJsonKey: 'companyId',
    createKeys: new Set(['companyName', 'parentCompanyId', 'ownershipPct', 'baseCurrency', 'slug', 'alias']),
    patchKeys: new Set([
      'companyName',
      'slug',
      'alias',
      'clearSlug',
      'clearAlias',
      'baseCurrency',
      'parentCompanyId',
      'clearParentCompany',
      'ownershipPct',
      'clearOwnershipPct',
      'defaultPortalBuId',
      'clearDefaultPortalBu',
    ]),
  },
  property: {
    pathSegment: 'properties',
    idJsonKey: 'propertyId',
    createKeys: new Set(['companyId', 'locationId', 'propertyName', 'propertyType']),
    patchKeys: new Set(['propertyName', 'propertyType']),
  },
  location: {
    pathSegment: 'locations',
    idJsonKey: 'locationId',
    createKeys: new Set([
      'locationName',
      'addressLine1',
      'city',
      'stateProvince',
      'postalCode',
      'countryCode',
      'regionId',
    ]),
    patchKeys: new Set([
      'locationName',
      'addressLine1',
      'city',
      'stateProvince',
      'postalCode',
      'countryCode',
      'regionId',
      'clearRegion',
    ]),
  },
  region: {
    pathSegment: 'regions',
    idJsonKey: 'regionId',
    createKeys: new Set(['parentRegionId', 'regionCode', 'regionName']),
    patchKeys: new Set(['regionCode', 'regionName', 'parentRegionId', 'clearParentRegion']),
  },
  business_unit: {
    pathSegment: 'business-units',
    idJsonKey: 'buId',
    createKeys: new Set(['companyId', 'parentBuId', 'buType', 'buName']),
    patchKeys: new Set(['buType', 'buName', 'parentBuId', 'clearParentBu']),
  },
  property_unit: {
    pathSegment: 'property-units',
    idJsonKey: 'unitId',
    createKeys: new Set(['propertyId', 'unitNumber', 'squareFootage', 'status']),
    patchKeys: new Set(['unitNumber', 'squareFootage', 'status', 'clearSquareFootage']),
  },
};

export function isCoreServiceHybridEntitySlug(slug: string | null | undefined): boolean {
  return Boolean(slug && HYBRID_SPECS[slug]);
}

export function entityHasCoreDomainFields(fields: EntityFieldDto[]): boolean {
  return fields.some(isCoreDomainField);
}

function snakeColumnToJsonKey(column: string): string {
  return column.replace(/_([a-z])/g, (_, c: string) => c.toUpperCase());
}

const UUID_RE = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$/;

function parseUuid(raw: unknown): string | null {
  if (raw === null || raw === undefined) return null;
  const s = String(raw).trim();
  if (!s) return null;
  return UUID_RE.test(s) ? s : null;
}

function coerceForCoreJson(field: EntityFieldDto, raw: unknown): unknown {
  const ft = (field.fieldType || '').toLowerCase();
  if (ft === 'number') {
    if (raw === null || raw === undefined || raw === '') return null;
    if (typeof raw === 'number' && !Number.isNaN(raw)) return raw;
    const s = String(raw).trim();
    if (!s) return null;
    const n = Number(s);
    return Number.isFinite(n) ? n : null;
  }
  if (raw === null || raw === undefined) return null;
  if (typeof raw === 'string') {
    const t = raw.trim();
    return t === '' ? null : t;
  }
  return raw;
}

function coreFieldToJsonEntries(
  fields: EntityFieldDto[],
  values: Record<string, unknown>
): [string, unknown][] {
  const out: [string, unknown][] = [];
  for (const f of fields) {
    if (!isCoreDomainField(f)) continue;
    const col = readCoreBindingColumn(f);
    if (!col) continue;
    const jsonKey = snakeColumnToJsonKey(col);
    const v = coerceForCoreJson(f, values[f.slug]);
    out.push([jsonKey, v]);
  }
  return out;
}

function buildCreateBody(entitySlug: string, fields: EntityFieldDto[], values: Record<string, unknown>): Record<string, unknown> {
  const spec = HYBRID_SPECS[entitySlug];
  if (!spec) return {};
  const body: Record<string, unknown> = {};
  for (const [k, v] of coreFieldToJsonEntries(fields, values)) {
    if (!spec.createKeys.has(k)) continue;
    if (v === null || v === undefined) continue;
    if (k.endsWith('Id') || k === 'companyId' || k === 'locationId' || k === 'propertyId' || k === 'parentRegionId' || k === 'parentBuId' || k === 'parentCompanyId' || k === 'regionId') {
      const id = parseUuid(v);
      if (id) body[k] = id;
      continue;
    }
    body[k] = v;
  }
  return body;
}

function buildPatchBody(entitySlug: string, fields: EntityFieldDto[], values: Record<string, unknown>): Record<string, unknown> {
  const spec = HYBRID_SPECS[entitySlug];
  if (!spec) return {};
  const body: Record<string, unknown> = {};
  for (const [k, v] of coreFieldToJsonEntries(fields, values)) {
    if (!spec.patchKeys.has(k)) continue;
    if (k === 'alias' || k === 'slug') {
      if (v === null || v === undefined || (typeof v === 'string' && v.trim() === '')) {
        if (k === 'alias') body.clearAlias = true;
        else body.clearSlug = true;
      } else {
        body[k] = typeof v === 'string' ? v.trim() : v;
      }
      continue;
    }
    if (k.endsWith('Id') || k === 'defaultPortalBuId') {
      const id = parseUuid(v);
      if (id) {
        body[k] = id;
      } else if (v === null || v === '') {
        if (k === 'parentCompanyId') body.clearParentCompany = true;
        else if (k === 'parentBuId') body.clearParentBu = true;
        else if (k === 'parentRegionId') body.clearParentRegion = true;
        else if (k === 'regionId') body.clearRegion = true;
        else if (k === 'defaultPortalBuId') body.clearDefaultPortalBu = true;
      }
      continue;
    }
    if (k === 'ownershipPct' && (v === null || v === '')) {
      body.clearOwnershipPct = true;
      continue;
    }
    if (k === 'squareFootage' && (v === null || v === '')) {
      body.clearSquareFootage = true;
      continue;
    }
    if (v === null || v === undefined) continue;
    if (typeof v === 'string' && v.trim() === '') continue;
    body[k] = v;
  }
  return body;
}

async function parseJsonOrThrow(res: Response): Promise<Record<string, unknown>> {
  if (!res.ok) throw new Error(await readApiError(res));
  return (await res.json()) as Record<string, unknown>;
}

function basePath(tenantId: string, entitySlug: string): string {
  const spec = HYBRID_SPECS[entitySlug];
  if (!spec) throw new Error(`Unknown hybrid entity slug: ${entitySlug}`);
  return `/v1/tenants/${tenantId}/${spec.pathSegment}`;
}

export async function createCoreMasterRow(
  tenantId: string,
  entitySlug: string,
  fields: EntityFieldDto[],
  values: Record<string, unknown>
): Promise<string> {
  const spec = HYBRID_SPECS[entitySlug];
  if (!spec) throw new Error(`Unknown hybrid entity slug: ${entitySlug}`);
  const body = buildCreateBody(entitySlug, fields, values);
  const res = await apiFetch(basePath(tenantId, entitySlug), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  const json = await parseJsonOrThrow(res);
  const id = json[spec.idJsonKey];
  if (typeof id !== 'string' || !parseUuid(id)) {
    throw new Error(`Core service response missing ${spec.idJsonKey}`);
  }
  return id;
}

export async function patchCoreMasterRow(
  tenantId: string,
  entitySlug: string,
  coreId: string,
  fields: EntityFieldDto[],
  values: Record<string, unknown>
): Promise<void> {
  const spec = HYBRID_SPECS[entitySlug];
  if (!spec) throw new Error(`Unknown hybrid entity slug: ${entitySlug}`);
  const body = buildPatchBody(entitySlug, fields, values);
  if (Object.keys(body).length === 0) return;
  const res = await apiFetch(`${basePath(tenantId, entitySlug)}/${encodeURIComponent(coreId)}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(await readApiError(res));
}

export async function getCoreMasterRow(
  tenantId: string,
  entitySlug: string,
  coreId: string
): Promise<Record<string, unknown>> {
  const res = await apiFetch(`${basePath(tenantId, entitySlug)}/${encodeURIComponent(coreId)}`);
  return parseJsonOrThrow(res);
}

/** Map core-service JSON (camelCase) back to entity field slugs for the form. */
export function coreDtoToFormValues(fields: EntityFieldDto[], dto: Record<string, unknown>): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const f of fields) {
    if (!isCoreDomainField(f)) continue;
    const col = readCoreBindingColumn(f);
    if (!col) continue;
    const key = snakeColumnToJsonKey(col);
    if (!(key in dto) || dto[key] === undefined) continue;
    let v: unknown = dto[key];
    const ft = (f.fieldType || '').toLowerCase();
    if (ft === 'string' && v !== null && v !== undefined) {
      v = String(v);
    }
    out[f.slug] = v;
  }
  return out;
}

export function assertCoreDomainRoutedOrThrow(entitySlug: string, fields: EntityFieldDto[]): void {
  if (!entityHasCoreDomainFields(fields)) return;
  if (isCoreServiceHybridEntitySlug(entitySlug)) return;
  throw new Error(
    'This entity has CORE_DOMAIN fields that are not sent to the record API. Portal routing for this entity is not implemented — use the domain service API for core fields.'
  );
}

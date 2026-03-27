import { apiFetch } from './client';
import type { LayoutV2 } from '../types/formLayout';
import type { BusinessRuleDto } from '../utils/businessRuleUi';

export type { BusinessRuleDto } from '../utils/businessRuleUi';

export type DefinitionScope = 'STANDARD_OBJECT' | 'TENANT_OBJECT';

export type EntityDto = {
  id: string;
  tenantId: string;
  name: string;
  slug: string;
  description?: string | null;
  baseEntityId?: string | null;
  defaultDisplayFieldSlug?: string | null;
  status: string;
  categoryKey?: string | null;
  definitionScope?: DefinitionScope;
  createdAt: string;
  updatedAt: string;
};

/** Stored under {@link EntityFieldDto.config} as {@code documentNumberGeneration}. */
export type DocumentNumberGenerationStrategy =
  | 'MANUAL'
  | 'TIMESTAMP'
  | 'TENANT_SEQUENCE'
  | 'MONTHLY_SEQUENCE';

export type DocumentNumberGenerationConfig = {
  strategy: DocumentNumberGenerationStrategy;
  /** Prepended to generated numbers, e.g. {@code JV} → {@code JV2026050001}. */
  prefix?: string;
  /** Zero-padded width for the numeric suffix (sequential strategies). */
  sequenceWidth?: number;
  /** IANA zone id for month boundaries (e.g. {@code MONTHLY_SEQUENCE}). */
  timeZone?: string;
};

/** Use as {@link EntityFieldDto.fieldType}; value is stored on the record row, not EAV. */
export const DOCUMENT_NUMBER_FIELD_TYPE = 'document_number' as const;

export function isDocumentNumberFieldType(fieldType: string | null | undefined): boolean {
  return (fieldType || '').trim().toLowerCase() === DOCUMENT_NUMBER_FIELD_TYPE;
}

/**
 * Browser tracing for entity-status assignment API usage.
 *
 * Enable any one of:
 * - URL: append {@code ?debugEntityStatus=1} (or {@code &debugEntityStatus=1}) and load/reload the app.
 * - Console: {@code localStorage.setItem('erpDebugEntityStatus', '1'); location.reload()}
 * - Session (same tab): {@code sessionStorage.setItem('erpDebugEntityStatus', '1'); location.reload()}
 *
 * Values {@code '1'} or {@code 'true'} (case-insensitive) count as on.
 * Disable: remove the query param; {@code localStorage.removeItem('erpDebugEntityStatus')}; etc.
 *
 * Uses {@code console.log} so messages show under the default “All levels” Console filter (some setups hide “Info” only).
 */
export const DEBUG_ENTITY_STATUS_KEY = 'erpDebugEntityStatus';

function readDebugEntityStatusFlag(getter: () => string | null): boolean {
  const raw = getter()?.trim().toLowerCase();
  return raw === '1' || raw === 'true';
}

export function isEntityStatusAssignmentDebugEnabled(): boolean {
  try {
    if (typeof window === 'undefined') return false;
    const search = window.location?.search ?? '';
    const q = new URLSearchParams(search);
    if (readDebugEntityStatusFlag(() => q.get('debugEntityStatus'))) return true;
    if (readDebugEntityStatusFlag(() => q.get(DEBUG_ENTITY_STATUS_KEY))) return true;
    if (readDebugEntityStatusFlag(() => window.sessionStorage?.getItem(DEBUG_ENTITY_STATUS_KEY) ?? null)) {
      return true;
    }
    if (readDebugEntityStatusFlag(() => window.localStorage?.getItem(DEBUG_ENTITY_STATUS_KEY) ?? null)) {
      return true;
    }
    return false;
  } catch {
    return false;
  }
}

function debugEntityStatusRequest(kind: string, detail: Record<string, unknown>): void {
  if (!isEntityStatusAssignmentDebugEnabled()) return;
  console.log(`[${DEBUG_ENTITY_STATUS_KEY}] ${kind}`, detail);
}

/** Fields returned by the API may include {@code INACTIVE} archived definitions; record I/O and layouts use active fields only. */
export function activeEntityFields(fields: EntityFieldDto[]): EntityFieldDto[] {
  return fields.filter((f) => (f.status ?? 'ACTIVE').toUpperCase() === 'ACTIVE');
}

export type EntityFieldDto = {
  id: string;
  entityId: string;
  definitionScope?: DefinitionScope;
  name: string;
  slug: string;
  fieldType: string;
  required: boolean;
  pii: boolean;
  sortOrder: number;
  labelOverride?: string | null;
  /** Server-resolved label for current Accept-Language / X-User-Locale. */
  displayLabel?: string | null;
  /** Persisted translations (locale → label). */
  labels?: Record<string, string> | null;
  formatString?: string | null;
  status: string;
  /** When true, value contributes to the basic list &quot;Display&quot; column (ordered by sortOrder). */
  includeInListSummaryDisplay?: boolean;
  createdAt: string;
  updatedAt: string;
  /**
   * JSON bag. Reference fields: {@code targetEntitySlug}; optional {@code referenceLookupDisplaySlugs} (string[], max 12)
   * for extra columns; optional {@code referenceUiMode} {@code "search"} (default) or {@code "dropdown"}.
   */
  config?: Record<string, unknown> | null;
};

export type FormLayoutDto = {
  id: string;
  tenantId: string;
  entityId: string;
  name: string;
  isDefault: boolean;
  status: string;
  layout: Record<string, unknown>;
  createdAt: string;
  updatedAt: string;
};

export type FormLayoutTemplateDto = {
  templateKey: string;
  title: string;
  description: string;
  tags: string[];
  layout: Record<string, unknown> | null;
};

export type NavigationItemDto = {
  id: string;
  parentId: string | null;
  sortOrder: number;
  routePath: string | null;
  label: string;
  description: string | null;
  type: string;
  icon: string | null;
  categoryKey: string | null;
  searchKeywords: string[];
  designStatus?: 'PUBLISHED' | 'WIP';
  linkedListViewId?: string | null;
  linkedFormLayoutId?: string | null;
  children: NavigationItemDto[];
};

export type NavigationResponse = {
  items: NavigationItemDto[];
};

export async function getNavigation(): Promise<NavigationResponse> {
  const res = await apiFetch('/v1/navigation');
  if (!res.ok) throw new Error(await readApiError(res));
  return (await res.json()) as NavigationResponse;
}

export type NavigationItemScope = 'TENANT' | 'GLOBAL';

export type NavigationItemCreateRequest = {
  parentId?: string | null;
  sortOrder: number;
  routePath: string;
  label: string;
  description?: string | null;
  type?: string;
  icon?: string | null;
  categoryKey?: string | null;
  searchKeywords?: string[];
  requiredPermissions?: string[];
  requiredRoles?: string[];
  scope: NavigationItemScope;
  designStatus?: 'PUBLISHED' | 'WIP';
  linkedListViewId?: string | null;
  linkedFormLayoutId?: string | null;
};

export type NavigationItemCreatedResponse = {
  id: string;
};

export async function createNavigationItem(
  body: NavigationItemCreateRequest
): Promise<NavigationItemCreatedResponse> {
  const res = await apiFetch('/v1/navigation/items', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(await readApiError(res));
  return (await res.json()) as NavigationItemCreatedResponse;
}

export type NavigationItemPatchRequest = {
  parentId?: string | null;
  sortOrder?: number;
  routePath?: string;
  label?: string;
  description?: string | null;
  icon?: string | null;
  categoryKey?: string | null;
  type?: string;
  searchKeywords?: string[];
  requiredPermissions?: string[];
  requiredRoles?: string[];
  active?: boolean;
  promoteToGlobal?: boolean;
  designStatus?: 'PUBLISHED' | 'WIP';
  linkedListViewId?: string | null;
  linkedFormLayoutId?: string | null;
};

export async function patchNavigationItem(
  id: string,
  body: NavigationItemPatchRequest
): Promise<void> {
  const res = await apiFetch(`/v1/navigation/items/${id}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(await readApiError(res));
}

export async function deleteNavigationItem(id: string): Promise<void> {
  const res = await apiFetch(`/v1/navigation/items/${id}`, { method: 'DELETE' });
  if (!res.ok) throw new Error(await readApiError(res));
}

export type NavigationAdminItemDto = {
  id: string;
  parentId: string | null;
  tenantId: string | null;
  sortOrder: number;
  routePath: string | null;
  label: string;
  description: string | null;
  type: string;
  icon: string | null;
  categoryKey: string | null;
  searchKeywords: string[];
  requiredPermissions: string[];
  requiredRoles: string[];
  active: boolean;
  designStatus?: 'PUBLISHED' | 'WIP';
  linkedListViewId?: string | null;
  linkedFormLayoutId?: string | null;
};

export type NavigationAdminListResponse = {
  items: NavigationAdminItemDto[];
};

export async function listNavigationAdminItems(): Promise<NavigationAdminListResponse> {
  const res = await apiFetch('/v1/navigation/items');
  if (!res.ok) throw new Error(await readApiError(res));
  return (await res.json()) as NavigationAdminListResponse;
}

export type OmniboxItem = {
  id: string;
  category: string;
  title: string;
  subtitle: string;
  url: string;
  icon: string | null;
  meta?: Record<string, unknown> | null;
};

export type OmniboxResponse = {
  navigation: OmniboxItem[];
  records: OmniboxItem[];
  deepHistory: OmniboxItem[];
};

export async function fetchOmnibox(
  q: string,
  signal?: AbortSignal,
  limitPerCategory?: number
): Promise<OmniboxResponse> {
  const p = new URLSearchParams({ q });
  if (limitPerCategory != null) {
    p.set('limitPerCategory', String(limitPerCategory));
  }
  const res = await apiFetch(`/v1/search/omnibox?${p}`, { signal });
  if (!res.ok) throw new Error(await readApiError(res));
  return (await res.json()) as OmniboxResponse;
}

function isRecord(v: unknown): v is Record<string, unknown> {
  return v !== null && typeof v === 'object' && !Array.isArray(v);
}

/** Formats entity-builder / core-service API error bodies for display (includes validation field errors when present). */
export function formatApiErrorBody(
  message: string,
  code: string | undefined,
  details: unknown
): string {
  let extra = '';
  if (isRecord(details)) {
    const fieldErrors = details.fieldErrors;
    if (Array.isArray(fieldErrors) && fieldErrors.length > 0) {
      const lines = fieldErrors.map((fe) => {
        if (!isRecord(fe)) return '';
        const f = typeof fe.field === 'string' ? fe.field : 'field';
        const m = typeof fe.message === 'string' ? fe.message : '';
        return m ? `${f}: ${m}` : f;
      });
      extra = lines.filter(Boolean).join('\n');
    }
    if (!extra) {
      const violations = details.violations;
      if (Array.isArray(violations) && violations.length > 0) {
        const lines = violations.map((v) => {
          if (!isRecord(v)) return '';
          const p = typeof v.path === 'string' ? v.path : 'constraint';
          const m = typeof v.message === 'string' ? v.message : '';
          return m ? `${p}: ${m}` : p;
        });
        extra = lines.filter(Boolean).join('\n');
      }
    }
  }
  const head = `${message}${code ? ` (${code})` : ''}`;
  return extra ? `${head}\n${extra}` : head;
}

export async function readApiError(res: Response): Promise<string> {
  try {
    const j = (await res.json()) as Record<string, unknown>;
    const nested = j.error;
    if (isRecord(nested) && typeof nested.message === 'string') {
      const code = typeof nested.code === 'string' ? nested.code : undefined;
      return formatApiErrorBody(nested.message, code, nested.details);
    }
    if (typeof j.message === 'string') {
      const code = typeof j.code === 'string' ? j.code : undefined;
      return formatApiErrorBody(j.message, code, j.details);
    }
  } catch {
    /* ignore */
  }
  return `Request failed (${res.status})`;
}

/** Thrown when {@link apiFetch} returns a non-OK response (optional; not all APIs use this yet). */
export class ApiHttpError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = 'ApiHttpError';
    this.status = status;
  }
}

/** Upserts classpath system-entity-catalog manifests into the tenant (requires entity_builder:schema:write). */
export async function syncSystemEntityCatalog(
  tenantId: string,
  manifestKey?: string
): Promise<{ syncedManifestKeys: string[] }> {
  const q = manifestKey ? `?manifestKey=${encodeURIComponent(manifestKey)}` : '';
  const res = await apiFetch(`/v1/tenants/${tenantId}/catalog/sync${q}`, { method: 'POST' });
  if (!res.ok) throw new ApiHttpError(res.status, await readApiError(res));
  const data = (await res.json()) as { syncedManifestKeys?: string[] };
  return { syncedManifestKeys: data.syncedManifestKeys ?? [] };
}

/** DDL import (entity-builder {@code /v1/entities/import/ddl}). */
export type DdlImportPreviewRequest = {
  ddl: string;
  storageMode?: string;
  coreBindingService?: string | null;
  excludeAuditColumns?: boolean | null;
  createRelationshipsFromForeignKeys?: boolean | null;
  skipColumnSlugs?: string[];
  categoryKey?: string | null;
};

export type DdlImportApplyRequest = DdlImportPreviewRequest & {
  tableOverrides?: DdlTableApplyOverride[];
};

export type DdlTableApplyOverride = {
  parsedEntitySlug?: string | null;
  entityName?: string | null;
  entitySlug?: string | null;
};

export type DdlFieldPreviewDto = {
  columnName: string;
  fieldSlug: string;
  sqlDataType: string;
  proposedFieldType: string;
  required: boolean;
  primaryKey: boolean;
  fkTargetTable?: string | null;
  fkTargetEntitySlug?: string | null;
  targetEntityResolvable?: boolean | null;
  warnings: string[];
};

export type DdlRelationshipPreviewDto = {
  relationshipSlug: string;
  name: string;
  cardinality: string;
  fromEntitySlug: string;
  toEntitySlug: string;
  fromFieldSlug?: string | null;
  toFieldSlug?: string | null;
  creatableAfterImport: boolean;
  skipReason?: string | null;
};

export type DdlTablePreviewDto = {
  rawTableName: string;
  proposedEntitySlug: string;
  proposedEntityName: string;
  defaultDisplayFieldSlug?: string | null;
  fields: DdlFieldPreviewDto[];
  relationships: DdlRelationshipPreviewDto[];
};

export type DdlImportPreviewResponse = {
  tables: DdlTablePreviewDto[];
  warnings: string[];
};

export type DdlCreatedFieldSummary = {
  id: string;
  slug: string;
  fieldType: string;
};

export type DdlCreatedEntitySummary = {
  id: string;
  slug: string;
  name: string;
  fields: DdlCreatedFieldSummary[];
};

export type DdlCreatedRelationshipSummary = {
  id: string;
  slug: string;
};

export type DdlImportApplyResponse = {
  entities: DdlCreatedEntitySummary[];
  relationships: DdlCreatedRelationshipSummary[];
};

export async function previewDdlImport(body: DdlImportPreviewRequest): Promise<DdlImportPreviewResponse> {
  const res = await apiFetch('/v1/entities/import/ddl/preview', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new ApiHttpError(res.status, await readApiError(res));
  return (await res.json()) as DdlImportPreviewResponse;
}

export async function applyDdlImport(body: DdlImportApplyRequest): Promise<DdlImportApplyResponse> {
  const res = await apiFetch('/v1/entities/import/ddl', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new ApiHttpError(res.status, await readApiError(res));
  return (await res.json()) as DdlImportApplyResponse;
}

export async function listEntities(params?: { q?: string; categoryKey?: string }): Promise<EntityDto[]> {
  const q = new URLSearchParams();
  if (params?.q != null && params.q.trim() !== '') q.set('q', params.q.trim());
  if (params?.categoryKey != null && params.categoryKey.trim() !== '') q.set('categoryKey', params.categoryKey.trim());
  const qs = q.toString();
  const res = await apiFetch(`/v1/entities${qs ? `?${qs}` : ''}`);
  if (!res.ok) throw new ApiHttpError(res.status, await readApiError(res));
  return (await res.json()) as EntityDto[];
}

export async function getEntity(entityId: string): Promise<EntityDto> {
  const res = await apiFetch(`/v1/entities/${entityId}`);
  if (!res.ok) throw new Error(await readApiError(res));
  return (await res.json()) as EntityDto;
}

export async function createEntity(body: {
  name: string;
  slug: string;
  description?: string | null;
  status?: string | null;
  /** Requires platform schema write when {@code STANDARD_OBJECT}. */
  definitionScope?: DefinitionScope;
}): Promise<EntityDto> {
  const payload: Record<string, unknown> = {
    name: body.name,
    slug: body.slug,
    description: body.description ?? undefined,
    status: body.status ?? 'ACTIVE',
  };
  if (body.definitionScope != null) {
    payload.definitionScope = body.definitionScope;
  }
  const res = await apiFetch('/v1/entities', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
  if (!res.ok) throw new Error(await readApiError(res));
  return (await res.json()) as EntityDto;
}

export async function patchEntity(
  entityId: string,
  body: Partial<{
    name: string;
    slug: string;
    description: string | null;
    status: string;
    defaultDisplayFieldSlug: string | null;
    clearDefaultDisplayField: boolean;
    /** Requires platform schema write when changing to/from {@code STANDARD_OBJECT}. */
    definitionScope: DefinitionScope;
  }>
): Promise<EntityDto> {
  const res = await apiFetch(`/v1/entities/${entityId}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(await readApiError(res));
  return (await res.json()) as EntityDto;
}

export async function listFields(entityId: string): Promise<EntityFieldDto[]> {
  const res = await apiFetch(`/v1/entities/${entityId}/fields`);
  if (!res.ok) throw new Error(await readApiError(res));
  return (await res.json()) as EntityFieldDto[];
}

export async function createField(
  entityId: string,
  body: {
    name: string;
    slug: string;
    fieldType: string;
    required: boolean;
    pii: boolean;
    config?: Record<string, unknown> | null;
    includeInListSummaryDisplay?: boolean;
  }
): Promise<EntityFieldDto> {
  const res = await apiFetch(`/v1/entities/${entityId}/fields`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(await readApiError(res));
  return (await res.json()) as EntityFieldDto;
}

/** Empty or null {@code label} removes the row for that locale. */
export async function putFieldLabel(
  entityId: string,
  fieldId: string,
  locale: string,
  label: string | null
): Promise<EntityFieldDto> {
  const res = await apiFetch(`/v1/entities/${entityId}/fields/${fieldId}/labels/${encodeURIComponent(locale)}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ label: label ?? '' }),
  });
  if (!res.ok) throw new Error(await readApiError(res));
  return (await res.json()) as EntityFieldDto;
}

/** {@code outcome} is {@code DELETED} (hard remove) or {@code DEACTIVATED} (field retained for historical data). */
export async function deleteField(
  entityId: string,
  fieldId: string
): Promise<{ outcome: 'DELETED' | 'DEACTIVATED' }> {
  const res = await apiFetch(`/v1/entities/${entityId}/fields/${fieldId}`, { method: 'DELETE' });
  if (!res.ok) throw new Error(await readApiError(res));
  return (await res.json()) as { outcome: 'DELETED' | 'DEACTIVATED' };
}

export async function patchField(
  entityId: string,
  fieldId: string,
  body: Partial<{
    name: string;
    slug: string;
    fieldType: string;
    required: boolean;
    pii: boolean;
    sortOrder: number;
    labelOverride: string | null;
    formatString: string | null;
    config: Record<string, unknown> | null;
    includeInListSummaryDisplay?: boolean;
  }>
): Promise<EntityFieldDto> {
  const res = await apiFetch(`/v1/entities/${entityId}/fields/${fieldId}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(await readApiError(res));
  return (await res.json()) as EntityFieldDto;
}

/** Schema relationship for record links; FK-style reference fields typically use {@code from}=target entity, {@code to}=entity that holds the field. */
export type EntityRelationshipDto = {
  id: string;
  tenantId: string;
  name: string;
  slug: string;
  fromEntityId: string;
  toEntityId: string;
  fromFieldSlug: string | null;
  toFieldSlug: string | null;
  cardinality: string;
  definitionScope?: DefinitionScope;
  createdAt: string;
  updatedAt: string;
};

export async function listEntityRelationships(): Promise<EntityRelationshipDto[]> {
  const res = await apiFetch('/v1/entity-relationships');
  if (!res.ok) throw new Error(await readApiError(res));
  return (await res.json()) as EntityRelationshipDto[];
}

export async function createEntityRelationship(body: {
  name: string;
  slug: string;
  cardinality: string;
  fromEntityId: string;
  toEntityId: string;
  fromFieldSlug?: string | null;
  toFieldSlug?: string | null;
}): Promise<EntityRelationshipDto> {
  const res = await apiFetch('/v1/entity-relationships', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(await readApiError(res));
  return (await res.json()) as EntityRelationshipDto;
}

export async function patchEntityRelationship(
  id: string,
  body: Partial<{
    name: string;
    slug: string;
    cardinality: string;
    fromFieldSlug: string | null;
    toFieldSlug: string | null;
  }>
): Promise<EntityRelationshipDto> {
  const res = await apiFetch(`/v1/entity-relationships/${id}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(await readApiError(res));
  return (await res.json()) as EntityRelationshipDto;
}

export async function deleteEntityRelationship(id: string): Promise<void> {
  const res = await apiFetch(`/v1/entity-relationships/${id}`, { method: 'DELETE' });
  if (!res.ok) throw new Error(await readApiError(res));
}

export type RecordLookupItemDto = {
  recordId: string;
  displayLabel: string;
  values: Record<string, unknown>;
};

export type RecordLookupResponse = {
  items: RecordLookupItemDto[];
};

export async function lookupRecords(
  tenantId: string,
  entityId: string,
  params: {
    term: string;
    limit?: number;
    displaySlugs?: string[];
    assignedForEntityId?: string | null;
    assignedForEntityFieldId?: string | null;
  }
): Promise<RecordLookupResponse> {
  const q = new URLSearchParams();
  q.set('term', params.term);
  if (params.limit != null) q.set('limit', String(params.limit));
  for (const s of params.displaySlugs ?? []) {
    q.append('displaySlugs', s);
  }
  if (params.assignedForEntityId?.trim()) {
    q.set('assignedForEntityId', params.assignedForEntityId.trim());
  }
  if (params.assignedForEntityFieldId?.trim()) {
    q.set('assignedForEntityFieldId', params.assignedForEntityFieldId.trim());
  }
  const lookupPath = `/v1/tenants/${tenantId}/entities/${entityId}/records/lookup?${q.toString()}`;
  if (params.assignedForEntityId?.trim() || params.assignedForEntityFieldId?.trim()) {
    debugEntityStatusRequest('GET records/lookup (assignment scope via query params)', {
      path: lookupPath,
      assignedForEntityId: params.assignedForEntityId?.trim() || null,
      assignedForEntityFieldId: params.assignedForEntityFieldId?.trim() || null,
    });
  }
  const res = await apiFetch(lookupPath);
  if (!res.ok) throw new Error(await readApiError(res));
  return (await res.json()) as RecordLookupResponse;
}

export type RecordLinkDto = {
  relationshipSlug: string;
  toRecordId: string;
};

export type RecordDto = {
  id: string;
  tenantId: string;
  entityId: string;
  externalId: string | null;
  businessDocumentNumber: string | null;
  createdBy: string | null;
  updatedBy: string | null;
  status: string;
  values: Record<string, unknown>;
  links: RecordLinkDto[];
  createdAt: string;
  updatedAt: string;
  createdByLabel?: string | null;
  updatedByLabel?: string | null;
  entityStatusId?: string | null;
  entityStatusDisplayLabel?: string | null;
  /** Server-computed scalar-only list summary (two round-trips); references still resolved client-side when flagged. */
  listSummaryDisplay?: string | null;
};

export type PageResponse<T> = {
  items: T[];
  page: number;
  pageSize: number;
  total: number;
};

/** Filter tree for POST .../records/query (see entity-builder RecordQueryDtos). */
export type RecordQueryFilterNode = {
  op?: string;
  field?: string;
  value?: unknown;
  children?: RecordQueryFilterNode[];
};

/** Sort entity list query by record row timestamps. */
export type RecordQuerySort = {
  field?: 'record.updated_at' | 'record.created_at';
  direction?: 'asc' | 'desc';
};

export type RecordQueryRequest = {
  filter?: RecordQueryFilterNode;
  sort?: RecordQuerySort;
  page?: number;
  pageSize?: number;
};

export async function listRecords(
  tenantId: string,
  entityId: string,
  params?: {
    page?: number;
    pageSize?: number;
    assignedForEntityId?: string | null;
    assignedForEntityFieldId?: string | null;
  }
): Promise<PageResponse<RecordDto>> {
  const q = new URLSearchParams();
  if (params?.page != null) q.set('page', String(params.page));
  if (params?.pageSize != null) q.set('pageSize', String(params.pageSize));
  if (params?.assignedForEntityId?.trim()) {
    q.set('assignedForEntityId', params.assignedForEntityId.trim());
  }
  if (params?.assignedForEntityFieldId?.trim()) {
    q.set('assignedForEntityFieldId', params.assignedForEntityFieldId.trim());
  }
  const qs = q.toString();
  const listPath = `/v1/tenants/${tenantId}/entities/${entityId}/records${qs ? `?${qs}` : ''}`;
  if (params?.assignedForEntityId?.trim() || params?.assignedForEntityFieldId?.trim()) {
    debugEntityStatusRequest('GET records (assignment scope via query params)', {
      path: listPath,
      assignedForEntityId: params?.assignedForEntityId?.trim() || null,
      assignedForEntityFieldId: params?.assignedForEntityFieldId?.trim() || null,
      page: params?.page ?? null,
      pageSize: params?.pageSize ?? null,
    });
  }
  const res = await apiFetch(listPath);
  if (!res.ok) throw new Error(await readApiError(res));
  return (await res.json()) as PageResponse<RecordDto>;
}

export type EntityStatusAssignmentRowDto = {
  entityStatusId: string;
  code: string;
  label: string;
  sortOrder: number;
};

export type EntityStatusAvailableDto = {
  entityStatusId: string;
  code: string;
  label: string;
};

export async function getEntityStatusAssignments(
  tenantId: string,
  entityDefinitionId: string
): Promise<EntityStatusAssignmentRowDto[]> {
  const res = await apiFetch(
    `/v1/tenants/${tenantId}/entities/${entityDefinitionId}/entity-status-assignments`
  );
  if (!res.ok) throw new Error(await readApiError(res));
  return (await res.json()) as EntityStatusAssignmentRowDto[];
}

export async function getAssignableEntityStatuses(
  tenantId: string,
  entityDefinitionId: string
): Promise<EntityStatusAvailableDto[]> {
  const res = await apiFetch(
    `/v1/tenants/${tenantId}/entities/${entityDefinitionId}/entity-status-assignments/available`
  );
  if (!res.ok) throw new Error(await readApiError(res));
  return (await res.json()) as EntityStatusAvailableDto[];
}

export async function putEntityStatusAssignments(
  tenantId: string,
  entityDefinitionId: string,
  entityStatusIds: string[]
): Promise<EntityStatusAssignmentRowDto[]> {
  const res = await apiFetch(
    `/v1/tenants/${tenantId}/entities/${entityDefinitionId}/entity-status-assignments`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ entityStatusIds }),
    }
  );
  if (!res.ok) throw new Error(await readApiError(res));
  return (await res.json()) as EntityStatusAssignmentRowDto[];
}

export async function getEntityStatusAssignmentsForField(
  entityId: string,
  fieldId: string
): Promise<EntityStatusAssignmentRowDto[]> {
  const path = `/v1/entities/${entityId}/fields/${fieldId}/entity-status-assignments`;
  debugEntityStatusRequest('GET entity-status-assignments (field scope)', { path });
  const res = await apiFetch(path);
  if (!res.ok) throw new Error(await readApiError(res));
  return (await res.json()) as EntityStatusAssignmentRowDto[];
}

export async function getAssignableEntityStatusesForField(
  entityId: string,
  fieldId: string
): Promise<EntityStatusAvailableDto[]> {
  const res = await apiFetch(`/v1/entities/${entityId}/fields/${fieldId}/entity-status-assignments/available`);
  if (!res.ok) throw new Error(await readApiError(res));
  return (await res.json()) as EntityStatusAvailableDto[];
}

export async function putEntityStatusAssignmentsForField(
  entityId: string,
  fieldId: string,
  entityStatusIds: string[]
): Promise<EntityStatusAssignmentRowDto[]> {
  const res = await apiFetch(`/v1/entities/${entityId}/fields/${fieldId}/entity-status-assignments`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ entityStatusIds }),
  });
  if (!res.ok) throw new Error(await readApiError(res));
  return (await res.json()) as EntityStatusAssignmentRowDto[];
}

export async function queryRecords(
  tenantId: string,
  entityId: string,
  body: RecordQueryRequest
): Promise<PageResponse<RecordDto>> {
  const res = await apiFetch(`/v1/tenants/${tenantId}/entities/${entityId}/records/query`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(await readApiError(res));
  return (await res.json()) as PageResponse<RecordDto>;
}

export async function getRecord(
  tenantId: string,
  entityId: string,
  recordId: string
): Promise<RecordDto> {
  const res = await apiFetch(`/v1/tenants/${tenantId}/entities/${entityId}/records/${recordId}`);
  if (!res.ok) throw new Error(await readApiError(res));
  return (await res.json()) as RecordDto;
}

/** Outgoing link from a record ({@code from}) to {@code toRecordId} via schema relationship slug. */
export type RecordLinkWriteDto = {
  relationshipSlug: string;
  toRecordId: string;
};

export async function listRecordLinks(tenantId: string, recordId: string): Promise<RecordLinkDto[]> {
  const res = await apiFetch(`/v1/tenants/${tenantId}/records/${recordId}/links`);
  if (!res.ok) throw new Error(await readApiError(res));
  return (await res.json()) as RecordLinkDto[];
}

export async function addRecordLink(
  tenantId: string,
  recordId: string,
  body: RecordLinkWriteDto
): Promise<void> {
  const res = await apiFetch(`/v1/tenants/${tenantId}/records/${recordId}/links`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(await readApiError(res));
}

export async function deleteRecordLink(
  tenantId: string,
  recordId: string,
  body: RecordLinkWriteDto
): Promise<void> {
  const res = await apiFetch(`/v1/tenants/${tenantId}/records/${recordId}/links`, {
    method: 'DELETE',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(await readApiError(res));
}

export async function getRecordByBusinessDocumentNumber(
  tenantId: string,
  entityId: string,
  businessDocumentNumber: string
): Promise<RecordDto> {
  const enc = encodeURIComponent(businessDocumentNumber);
  const res = await apiFetch(
    `/v1/tenants/${tenantId}/entities/${entityId}/records/by-business-document-number/${enc}`
  );
  if (!res.ok) throw new Error(await readApiError(res));
  return (await res.json()) as RecordDto;
}

export async function createRecord(
  tenantId: string,
  entityId: string,
  body: {
    values: Record<string, unknown>;
    links?: RecordLinkDto[];
    externalId?: string | null;
    businessDocumentNumber?: string | null;
  },
  idempotencyKey?: string
): Promise<RecordDto> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey;
  const res = await apiFetch(`/v1/tenants/${tenantId}/entities/${entityId}/records`, {
    method: 'POST',
    headers,
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(await readApiError(res));
  return (await res.json()) as RecordDto;
}

export async function patchRecord(
  tenantId: string,
  entityId: string,
  recordId: string,
  body: { values?: Record<string, unknown>; links?: RecordLinkDto[] }
): Promise<RecordDto> {
  const res = await apiFetch(`/v1/tenants/${tenantId}/entities/${entityId}/records/${recordId}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(await readApiError(res));
  return (await res.json()) as RecordDto;
}

export async function deleteRecord(
  tenantId: string,
  entityId: string,
  recordId: string
): Promise<void> {
  const res = await apiFetch(`/v1/tenants/${tenantId}/entities/${entityId}/records/${recordId}`, {
    method: 'DELETE',
  });
  if (!res.ok) throw new Error(await readApiError(res));
}

/** One row from entity-builder {@code audit_log} API. */
export type AuditEventDto = {
  id: string;
  createdAt: string;
  actorId: string | null;
  /** Tenant/user display name or email when IAM tables share the DB with entity-builder. */
  actorLabel?: string | null;
  action: string;
  operation: string | null;
  resourceType: string | null;
  resourceId: string | null;
  correlationId: string | null;
  sourceService: string | null;
  payload: unknown;
};

/** Text for the audit "Actor" column: API label, else {@code payload.actor}, else actor id. */
export function auditEventActorDisplay(e: AuditEventDto): string {
  const fromApi = e.actorLabel?.trim();
  if (fromApi) return fromApi;
  const act = payloadActor(e.payload);
  if (act) {
    const u = [act.tenantDisplayName, act.displayName, act.email].find(
      (x) => typeof x === 'string' && x.trim() !== ''
    );
    if (u) return u.trim();
  }
  return e.actorId ?? '—';
}

function payloadActor(payload: unknown): {
  tenantDisplayName?: string;
  displayName?: string;
  email?: string;
} | null {
  if (!payload || typeof payload !== 'object') return null;
  const a = (payload as Record<string, unknown>).actor;
  if (!a || typeof a !== 'object') return null;
  return a as { tenantDisplayName?: string; displayName?: string; email?: string };
}

export async function listRecordAuditEvents(
  tenantId: string,
  entityId: string,
  recordId: string,
  params?: { page?: number; pageSize?: number; from?: string; to?: string; actionPrefix?: string }
): Promise<PageResponse<AuditEventDto>> {
  const q = new URLSearchParams();
  if (params?.page != null) q.set('page', String(params.page));
  if (params?.pageSize != null) q.set('pageSize', String(params.pageSize));
  if (params?.from) q.set('from', params.from);
  if (params?.to) q.set('to', params.to);
  if (params?.actionPrefix) q.set('actionPrefix', params.actionPrefix);
  const qs = q.toString();
  const res = await apiFetch(
    `/v1/tenants/${tenantId}/entities/${entityId}/records/${recordId}/audit-events${qs ? `?${qs}` : ''}`
  );
  if (!res.ok) throw new Error(await readApiError(res));
  return (await res.json()) as PageResponse<AuditEventDto>;
}

export async function listEntityAuditEvents(
  tenantId: string,
  entityId: string,
  params?: { page?: number; pageSize?: number; from?: string; to?: string; actionPrefix?: string }
): Promise<PageResponse<AuditEventDto>> {
  const q = new URLSearchParams();
  if (params?.page != null) q.set('page', String(params.page));
  if (params?.pageSize != null) q.set('pageSize', String(params.pageSize));
  if (params?.from) q.set('from', params.from);
  if (params?.to) q.set('to', params.to);
  if (params?.actionPrefix) q.set('actionPrefix', params.actionPrefix);
  const qs = q.toString();
  const res = await apiFetch(
    `/v1/tenants/${tenantId}/entities/${entityId}/audit-events${qs ? `?${qs}` : ''}`
  );
  if (!res.ok) throw new Error(await readApiError(res));
  return (await res.json()) as PageResponse<AuditEventDto>;
}

export async function listFormLayouts(entityId: string): Promise<FormLayoutDto[]> {
  const res = await apiFetch(`/v1/entities/${entityId}/form-layouts`);
  if (!res.ok) throw new Error(await readApiError(res));
  return (await res.json()) as FormLayoutDto[];
}

export async function listBusinessRules(
  entityId: string,
  params?: { surface?: 'ALL' | 'UI' | 'SERVER'; formLayoutId?: string; activeOnly?: boolean }
): Promise<BusinessRuleDto[]> {
  const q = new URLSearchParams();
  if (params?.surface) q.set('surface', params.surface);
  if (params?.formLayoutId) q.set('formLayoutId', params.formLayoutId);
  if (params?.activeOnly !== undefined) q.set('activeOnly', String(params.activeOnly));
  const qs = q.toString();
  const res = await apiFetch(`/v1/entities/${entityId}/business-rules${qs ? `?${qs}` : ''}`);
  if (!res.ok) throw new Error(await readApiError(res));
  return (await res.json()) as BusinessRuleDto[];
}

export async function getFormLayout(entityId: string, layoutId: string): Promise<FormLayoutDto> {
  const res = await apiFetch(`/v1/entities/${entityId}/form-layouts/${layoutId}`);
  if (!res.ok) throw new Error(await readApiError(res));
  return (await res.json()) as FormLayoutDto;
}

export async function createFormLayout(
  entityId: string,
  body: { name: string; layout: LayoutV2; isDefault?: boolean; status?: 'ACTIVE' | 'WIP' }
): Promise<FormLayoutDto> {
  const res = await apiFetch(`/v1/entities/${entityId}/form-layouts`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      name: body.name,
      layout: body.layout,
      isDefault: body.isDefault ?? false,
      ...(body.status ? { status: body.status } : {}),
    }),
  });
  if (!res.ok) throw new Error(await readApiError(res));
  return (await res.json()) as FormLayoutDto;
}

export async function patchFormLayout(
  entityId: string,
  layoutId: string,
  body: { name?: string; layout?: LayoutV2; isDefault?: boolean; status?: string }
): Promise<FormLayoutDto> {
  const res = await apiFetch(`/v1/entities/${entityId}/form-layouts/${layoutId}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(await readApiError(res));
  return (await res.json()) as FormLayoutDto;
}

export async function createLayoutFromTemplate(
  entityId: string,
  body: { templateKey: string; name: string; isDefault?: boolean }
): Promise<FormLayoutDto> {
  const res = await apiFetch(`/v1/entities/${entityId}/form-layouts/from-template`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      templateKey: body.templateKey,
      name: body.name,
      isDefault: body.isDefault ?? false,
    }),
  });
  if (!res.ok) throw new Error(await readApiError(res));
  return (await res.json()) as FormLayoutDto;
}

export async function deleteFormLayout(entityId: string, layoutId: string): Promise<void> {
  const res = await apiFetch(`/v1/entities/${entityId}/form-layouts/${layoutId}`, { method: 'DELETE' });
  if (!res.ok) throw new Error(await readApiError(res));
}

export type RecordListViewDto = {
  id: string;
  tenantId: string;
  entityId: string;
  name: string;
  isDefault: boolean;
  status: string;
  definition: Record<string, unknown>;
  createdAt: string;
  updatedAt: string;
};

export async function listRecordListViews(entityId: string): Promise<RecordListViewDto[]> {
  const res = await apiFetch(`/v1/entities/${entityId}/record-list-views`);
  if (!res.ok) throw new Error(await readApiError(res));
  return (await res.json()) as RecordListViewDto[];
}

export async function getRecordListView(entityId: string, viewId: string): Promise<RecordListViewDto> {
  const res = await apiFetch(`/v1/entities/${entityId}/record-list-views/${viewId}`);
  if (!res.ok) throw new Error(await readApiError(res));
  return (await res.json()) as RecordListViewDto;
}

export async function createRecordListView(
  entityId: string,
  body: { name: string; definition: Record<string, unknown>; isDefault?: boolean; status?: 'ACTIVE' | 'WIP' }
): Promise<RecordListViewDto> {
  const res = await apiFetch(`/v1/entities/${entityId}/record-list-views`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      name: body.name,
      definition: body.definition,
      isDefault: body.isDefault ?? false,
      ...(body.status ? { status: body.status } : {}),
    }),
  });
  if (!res.ok) throw new Error(await readApiError(res));
  return (await res.json()) as RecordListViewDto;
}

export async function patchRecordListView(
  entityId: string,
  viewId: string,
  body: { name?: string; definition?: Record<string, unknown>; isDefault?: boolean; status?: string }
): Promise<RecordListViewDto> {
  const res = await apiFetch(`/v1/entities/${entityId}/record-list-views/${viewId}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(await readApiError(res));
  return (await res.json()) as RecordListViewDto;
}

export async function deleteRecordListView(entityId: string, viewId: string): Promise<void> {
  const res = await apiFetch(`/v1/entities/${entityId}/record-list-views/${viewId}`, { method: 'DELETE' });
  if (!res.ok) throw new Error(await readApiError(res));
}

export async function listFormLayoutTemplates(includeLayout = false): Promise<FormLayoutTemplateDto[]> {
  const q = includeLayout ? '?includeLayout=true' : '';
  const res = await apiFetch(`/v1/form-layout-templates${q}`);
  if (!res.ok) throw new Error(await readApiError(res));
  return (await res.json()) as FormLayoutTemplateDto[];
}

import { apiFetch } from './client';
import type { LayoutV2 } from '../types/formLayout';

export type EntityDto = {
  id: string;
  tenantId: string;
  name: string;
  slug: string;
  description?: string | null;
  baseEntityId?: string | null;
  defaultDisplayFieldSlug?: string | null;
  status: string;
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

export type EntityFieldDto = {
  id: string;
  entityId: string;
  name: string;
  slug: string;
  fieldType: string;
  required: boolean;
  pii: boolean;
  sortOrder: number;
  labelOverride?: string | null;
  formatString?: string | null;
  status: string;
  createdAt: string;
  updatedAt: string;
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

export async function readApiError(res: Response): Promise<string> {
  try {
    const j = (await res.json()) as { error?: { message?: string; code?: string } };
    if (j?.error?.message) {
      return `${j.error.message}${j.error.code ? ` (${j.error.code})` : ''}`;
    }
  } catch {
    /* ignore */
  }
  return `Request failed (${res.status})`;
}

export async function listEntities(params?: { q?: string; categoryKey?: string }): Promise<EntityDto[]> {
  const q = new URLSearchParams();
  if (params?.q != null && params.q.trim() !== '') q.set('q', params.q.trim());
  if (params?.categoryKey != null && params.categoryKey.trim() !== '') q.set('categoryKey', params.categoryKey.trim());
  const qs = q.toString();
  const res = await apiFetch(`/v1/entities${qs ? `?${qs}` : ''}`);
  if (!res.ok) throw new Error(await readApiError(res));
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
}): Promise<EntityDto> {
  const res = await apiFetch('/v1/entities', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      name: body.name,
      slug: body.slug,
      description: body.description ?? undefined,
      status: body.status ?? 'ACTIVE',
    }),
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
  params: { term: string; limit?: number; displaySlugs?: string[] }
): Promise<RecordLookupResponse> {
  const q = new URLSearchParams();
  q.set('term', params.term);
  if (params.limit != null) q.set('limit', String(params.limit));
  for (const s of params.displaySlugs ?? []) {
    q.append('displaySlugs', s);
  }
  const res = await apiFetch(`/v1/tenants/${tenantId}/entities/${entityId}/records/lookup?${q.toString()}`);
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
  createdBy: string;
  status: string;
  values: Record<string, unknown>;
  links: RecordLinkDto[];
  createdAt: string;
  updatedAt: string;
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

export type RecordQueryRequest = {
  filter?: RecordQueryFilterNode;
  page?: number;
  pageSize?: number;
};

export async function listRecords(
  tenantId: string,
  entityId: string,
  params?: { page?: number; pageSize?: number }
): Promise<PageResponse<RecordDto>> {
  const q = new URLSearchParams();
  if (params?.page != null) q.set('page', String(params.page));
  if (params?.pageSize != null) q.set('pageSize', String(params.pageSize));
  const qs = q.toString();
  const res = await apiFetch(
    `/v1/tenants/${tenantId}/entities/${entityId}/records${qs ? `?${qs}` : ''}`
  );
  if (!res.ok) throw new Error(await readApiError(res));
  return (await res.json()) as PageResponse<RecordDto>;
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
  action: string;
  operation: string | null;
  resourceType: string | null;
  resourceId: string | null;
  correlationId: string | null;
  sourceService: string | null;
  payload: unknown;
};

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

export async function getFormLayout(entityId: string, layoutId: string): Promise<FormLayoutDto> {
  const res = await apiFetch(`/v1/entities/${entityId}/form-layouts/${layoutId}`);
  if (!res.ok) throw new Error(await readApiError(res));
  return (await res.json()) as FormLayoutDto;
}

export async function createFormLayout(
  entityId: string,
  body: { name: string; layout: LayoutV2; isDefault?: boolean }
): Promise<FormLayoutDto> {
  const res = await apiFetch(`/v1/entities/${entityId}/form-layouts`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      name: body.name,
      layout: body.layout,
      isDefault: body.isDefault ?? false,
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
  body: { name: string; definition: Record<string, unknown>; isDefault?: boolean }
): Promise<RecordListViewDto> {
  const res = await apiFetch(`/v1/entities/${entityId}/record-list-views`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      name: body.name,
      definition: body.definition,
      isDefault: body.isDefault ?? false,
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

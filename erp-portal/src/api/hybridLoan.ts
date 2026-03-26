import { apiFetch } from './client';
import { syncSystemEntityCatalog } from './schemas';

export type LoanDto = {
  id: string;
  tenantId: string;
  status: string;
  requestedAmount: string;
  productCode: string | null;
  createdAt: string;
  updatedAt: string;
};

export type EntityDto = {
  id: string;
  tenantId: string;
  name: string;
  slug: string;
  description: string | null;
  categoryKey: string | null;
  definitionScope?: 'STANDARD_OBJECT' | 'TENANT_OBJECT';
};

export type RecordDto = {
  id: string;
  tenantId: string;
  entityId: string;
  externalId: string | null;
  businessDocumentNumber?: string | null;
  values: Record<string, unknown>;
};

function parseJson<T>(res: Response): Promise<T> {
  if (!res.ok) {
    return res.json().then((body) => {
      throw new Error((body as { message?: string }).message || `HTTP ${res.status}`);
    });
  }
  return res.json() as Promise<T>;
}

export async function createLoan(
  tenantId: string,
  body: { status: string; requestedAmount: string; productCode?: string }
): Promise<LoanDto> {
  const res = await apiFetch(`/v1/tenants/${tenantId}/loans`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  return parseJson<LoanDto>(res);
}

export async function getLoan(tenantId: string, loanId: string): Promise<LoanDto> {
  const res = await apiFetch(`/v1/tenants/${tenantId}/loans/${loanId}`);
  return parseJson<LoanDto>(res);
}

export async function syncCatalog(tenantId: string, manifestKey?: string): Promise<{ syncedManifestKeys: string[] }> {
  return syncSystemEntityCatalog(tenantId, manifestKey);
}

export async function getEntityBySlug(slug: string): Promise<EntityDto> {
  const res = await apiFetch(`/v1/entities/by-slug/${encodeURIComponent(slug)}`);
  return parseJson<EntityDto>(res);
}

export async function createExtensionRecord(
  tenantId: string,
  entityId: string,
  externalId: string,
  values: Record<string, unknown>
): Promise<RecordDto> {
  const res = await apiFetch(`/v1/tenants/${tenantId}/entities/${entityId}/records`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ externalId, values }),
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error((body as { message?: string }).message || `HTTP ${res.status}`);
  }
  return res.json() as Promise<RecordDto>;
}

export async function getRecordByExternalId(
  tenantId: string,
  entityId: string,
  externalId: string
): Promise<RecordDto | null> {
  const res = await apiFetch(
    `/v1/tenants/${tenantId}/entities/${entityId}/records/by-external-id/${encodeURIComponent(externalId)}`
  );
  if (res.status === 404) {
    return null;
  }
  return parseJson<RecordDto>(res);
}

/** Merged view for hybrid UI: core loan + EAV extension values (no core keys in record.values). */
export async function loadMergedLoanView(tenantId: string, loanId: string): Promise<{
  core: LoanDto;
  entityId: string;
  extensions: Record<string, unknown>;
}> {
  const [core, entity] = await Promise.all([getLoan(tenantId, loanId), getEntityBySlug('loan_application')]);
  const ext = await getRecordByExternalId(tenantId, entity.id, loanId);
  return {
    core,
    entityId: entity.id,
    extensions: ext?.values ?? {},
  };
}

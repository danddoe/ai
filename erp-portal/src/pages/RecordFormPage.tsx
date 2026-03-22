import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams, useLocation } from 'react-router-dom';
import {
  createRecord,
  getEntity,
  getRecord,
  isDocumentNumberFieldType,
  listFields,
  listFormLayouts,
  listRecordAuditEvents,
  patchRecord,
  type AuditEventDto,
  type EntityFieldDto,
  type FormLayoutDto,
} from '../api/schemas';
import { useAuth } from '../auth/AuthProvider';
import {
  LayoutV2RuntimeRenderer,
  validateRequiredInRegions,
} from '../components/runtime/LayoutV2RuntimeRenderer';
import { parseLayoutV2, regionsForWizardStep } from '../utils/layoutV2';
import type { LayoutV2 } from '../types/formLayout';

/** Locale date + local wall time with milliseconds (avoids Intl combos that throw in some runtimes). */
function formatSavedTimestamp(date: Date): string {
  const datePart = date.toLocaleDateString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  });
  const hh = String(date.getHours()).padStart(2, '0');
  const mm = String(date.getMinutes()).padStart(2, '0');
  const ss = String(date.getSeconds()).padStart(2, '0');
  const mss = String(date.getMilliseconds()).padStart(3, '0');
  return `${datePart}, ${hh}:${mm}:${ss}.${mss}`;
}

type RecordSaveFlash = { recordSaveFlash?: { at: string } };

export function RecordFormPage() {
  const { entityId = '', recordId = '' } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const { tenantId, canRecordsRead, canRecordsWrite, canPiiRead } = useAuth();
  const isCreate = recordId === 'new';

  const [entityName, setEntityName] = useState<string>('');
  const [layoutDto, setLayoutDto] = useState<FormLayoutDto | null>(null);
  const [layout, setLayout] = useState<LayoutV2 | null>(null);
  const [fields, setFields] = useState<EntityFieldDto[]>([]);
  const [values, setValues] = useState<Record<string, unknown>>({});
  const [wizardStep, setWizardStep] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [saveSuccess, setSaveSuccess] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<'form' | 'history'>('form');
  const [auditEvents, setAuditEvents] = useState<AuditEventDto[]>([]);
  const [auditTotal, setAuditTotal] = useState(0);
  const [auditLoading, setAuditLoading] = useState(false);
  const [auditError, setAuditError] = useState<string | null>(null);

  const wizardIds = layout?.runtime?.recordEntry?.wizard?.stepOrderRegionIds ?? [];
  const isWizard =
    layout?.runtime?.recordEntry?.flow === 'wizard' && wizardIds.length > 0;
  const wizardLast = isWizard && wizardStep >= wizardIds.length - 1;

  const regionsToRender = useMemo(() => {
    if (!layout) return [];
    if (isWizard) return regionsForWizardStep(layout, wizardStep);
    return layout.regions;
  }, [layout, isWizard, wizardStep]);

  const load = useCallback(async () => {
    if (!tenantId || !entityId || !recordId) return;
    if (!canRecordsRead) {
      setLoading(false);
      return;
    }
    setError(null);
    setLoading(true);
    try {
      const [ent, flds, layouts] = await Promise.all([
        getEntity(entityId),
        listFields(entityId),
        listFormLayouts(entityId),
      ]);
      setEntityName(ent.name);
      setFields(flds);
      const def = layouts.find((l) => l.isDefault) ?? null;
      setLayoutDto(def);
      if (!def) {
        setLayout(null);
        setValues({});
        return;
      }
      const parsed = parseLayoutV2(def.layout);
      setLayout(parsed);
      if (!parsed) {
        setError('Default layout is not valid layout v2 JSON.');
        setValues({});
        return;
      }

      if (isCreate) {
        setValues({});
        setWizardStep(0);
        return;
      }

      const rec = await getRecord(tenantId, entityId, recordId);
      setValues({ ...rec.values });
      setWizardStep(0);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load');
      setLayout(null);
      setValues({});
    } finally {
      setLoading(false);
    }
  }, [tenantId, entityId, recordId, canRecordsRead, isCreate]);

  useEffect(() => {
    void load();
  }, [load]);

  const loadAuditHistory = useCallback(async () => {
    if (!tenantId || !entityId || !recordId || isCreate) return;
    setAuditError(null);
    setAuditLoading(true);
    try {
      const page = await listRecordAuditEvents(tenantId, entityId, recordId, { page: 1, pageSize: 100 });
      setAuditEvents(page.items);
      setAuditTotal(page.total);
    } catch (e) {
      setAuditError(e instanceof Error ? e.message : 'Failed to load audit history');
      setAuditEvents([]);
      setAuditTotal(0);
    } finally {
      setAuditLoading(false);
    }
  }, [tenantId, entityId, recordId, isCreate]);

  useEffect(() => {
    if (activeTab === 'history' && !isCreate) {
      void loadAuditHistory();
    }
  }, [activeTab, isCreate, loadAuditHistory]);

  const onFieldChange = useCallback((slug: string, v: unknown) => {
    setValues((prev) => ({ ...prev, [slug]: v }));
  }, []);

  function buildSubmitValues(raw: Record<string, unknown>): Record<string, unknown> {
    const out: Record<string, unknown> = {};
    for (const f of fields) {
      if (isDocumentNumberFieldType(f.fieldType)) {
        continue;
      }
      let v = raw[f.slug];
      const ft = (f.fieldType || '').toLowerCase();
      if (v === '' || v === undefined) {
        v = null;
      } else if (ft === 'number' && typeof v === 'string' && v.trim() !== '') {
        v = v.trim();
      } else if (ft === 'boolean') {
        if (v === null) v = null;
        else v = Boolean(v);
      }
      out[f.slug] = v;
    }
    return out;
  }

  /** Values map may still hold a typed value for display; strip to top-level API property on create. */
  function businessDocumentNumberFromValues(raw: Record<string, unknown>): string | undefined {
    for (const f of fields) {
      if (!isDocumentNumberFieldType(f.fieldType)) continue;
      const v = raw[f.slug];
      if (v == null) continue;
      const s = String(v).trim();
      if (s !== '') return s;
    }
    return undefined;
  }

  function validateCurrentStep(): boolean {
    if (!layout) return false;
    const missing = validateRequiredInRegions(regionsToRender, fields, values);
    if (missing.length > 0) {
      window.alert(`Required fields: ${missing.join(', ')}`);
      return false;
    }
    return true;
  }

  async function save() {
    if (!tenantId || !layout || !canRecordsWrite) return;
    const missing = validateRequiredInRegions(layout.regions, fields, values);
    if (missing.length > 0) {
      window.alert(`Required fields: ${missing.join(', ')}`);
      return;
    }
    const payload = buildSubmitValues(values);
    const bdn = businessDocumentNumberFromValues(values);
    setSaving(true);
    setError(null);
    setSaveSuccess(null);
    try {
      if (isCreate) {
        const created = await createRecord(tenantId, entityId, {
          values: payload,
          ...(bdn !== undefined ? { businessDocumentNumber: bdn } : {}),
        });
        const savedAt = new Date().toISOString();
        navigate(`/entities/${entityId}/records/${created.id}`, {
          replace: true,
          state: { recordSaveFlash: { at: savedAt } },
        });
      } else {
        await patchRecord(tenantId, entityId, recordId, { values: payload });
        await load();
        setSaveSuccess(`Saved successfully at ${formatSavedTimestamp(new Date())}.`);
        void loadAuditHistory();
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Save failed');
    } finally {
      setSaving(false);
    }
  }

  if (!tenantId) {
    return (
      <div className="page-shell">
        <p role="alert" className="text-error">
          Missing <code>tenant_id</code> in access token.
        </p>
      </div>
    );
  }

  if (!canRecordsRead) {
    return (
      <div className="page-shell">
        <p role="alert" className="text-error">
          Missing <code>entity_builder:records:read</code> permission.
        </p>
      </div>
    );
  }

  return (
    <div className="page-shell page-shell-wide">
      <nav className="breadcrumb">
        <Link to="/entities">Entities</Link>
        <span aria-hidden> / </span>
        <Link to={`/entities/${entityId}/records`}>{entityName || '…'}</Link>
        <span aria-hidden> / </span>
        <span>{isCreate ? 'New record' : 'Edit record'}</span>
      </nav>
      <header className="page-header">
        <div>
          <h1 className="page-title">{isCreate ? 'New record' : 'Edit record'}</h1>
          <p className="page-sub">
            {layoutDto ? `Layout: ${layoutDto.name}` : 'No default layout'}
            {isWizard ? ` · Step ${wizardStep + 1} of ${wizardIds.length}` : ''}
          </p>
        </div>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
          <Link className="btn btn-secondary" to={`/entities/${entityId}/records`}>
            Back to list
          </Link>
          {isWizard && wizardStep > 0 && (
            <button
              type="button"
              className="btn btn-secondary"
              disabled={saving}
              onClick={() => setWizardStep((s) => Math.max(0, s - 1))}
            >
              Back
            </button>
          )}
          {isWizard && !wizardLast && (
            <button
              type="button"
              className="btn btn-primary"
              disabled={saving}
              onClick={() => {
                if (!validateCurrentStep()) return;
                setWizardStep((s) => s + 1);
              }}
            >
              Next
            </button>
          )}
          {canRecordsWrite && (!isWizard || wizardLast) && (
            <button type="button" className="btn btn-primary" disabled={saving || !layout} onClick={() => void save()}>
              {saving ? 'Saving…' : 'Save'}
            </button>
          )}
        </div>
      </header>
      {error && (
        <p role="alert" className="text-error">
          {error}
        </p>
      )}
      {saveSuccess && (
        <p role="status" className="text-success" aria-live="polite">
          {saveSuccess}
        </p>
      )}
      {!isCreate && (
        <div
          role="tablist"
          aria-label="Record view"
          style={{ display: 'flex', gap: 8, marginBottom: 16, flexWrap: 'wrap' }}
        >
          <button
            type="button"
            role="tab"
            aria-selected={activeTab === 'form'}
            className={activeTab === 'form' ? 'btn btn-primary' : 'btn btn-secondary'}
            onClick={() => setActiveTab('form')}
          >
            Form
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={activeTab === 'history'}
            className={activeTab === 'history' ? 'btn btn-primary' : 'btn btn-secondary'}
            onClick={() => setActiveTab('history')}
          >
            History
          </button>
        </div>
      )}
      {loading && <p className="builder-muted">Loading…</p>}
      {!loading && !layoutDto && (
        <p className="text-error">
          Set a <strong>default</strong> form layout for this entity before entering records. Open{' '}
          <Link to={`/entities/${entityId}/layouts#form-layouts`}>Form layouts</Link>, edit a layout, enable{' '}
          <strong>Default form layout for entity</strong>, and save.
        </p>
      )}
      {!loading && activeTab === 'history' && !isCreate && (
        <section aria-label="Audit history">
          {auditError && (
            <p role="alert" className="text-error">
              {auditError}
            </p>
          )}
          {auditLoading && <p className="builder-muted">Loading history…</p>}
          {!auditLoading && !auditError && (
            <>
              <p className="builder-muted" style={{ marginBottom: 12 }}>
                {auditTotal === 0
                  ? 'No audit events for this record.'
                  : `Showing ${auditEvents.length} of ${auditTotal} event(s) (newest first).`}
              </p>
              {auditEvents.length > 0 && (
                <div className="records-table-wrap">
                  <table className="records-table">
                    <thead>
                      <tr>
                        <th>When</th>
                        <th>Action</th>
                        <th>Operation</th>
                        <th>Actor</th>
                        <th>Payload</th>
                      </tr>
                    </thead>
                    <tbody>
                      {auditEvents.map((e) => (
                        <tr key={e.id}>
                          <td>{new Date(e.createdAt).toLocaleString()}</td>
                          <td>
                            <code>{e.action}</code>
                          </td>
                          <td>{e.operation ?? '—'}</td>
                          <td>
                            <code>{e.actorId ?? '—'}</code>
                          </td>
                          <td style={{ maxWidth: 360 }}>
                            <details>
                              <summary className="builder-muted" style={{ cursor: 'pointer' }}>
                                View JSON
                              </summary>
                              <pre
                                style={{
                                  marginTop: 8,
                                  fontSize: 11,
                                  overflow: 'auto',
                                  maxHeight: 200,
                                  textAlign: 'left',
                                }}
                              >
                                {JSON.stringify(e.payload, null, 2)}
                              </pre>
                            </details>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </>
          )}
        </section>
      )}
      {!loading && layout && activeTab === 'form' && (
        <LayoutV2RuntimeRenderer
          regions={regionsToRender}
          fields={fields}
          values={values}
          onChange={onFieldChange}
          disabled={!canRecordsWrite}
          canPiiRead={canPiiRead}
          useTabGroups={!isWizard}
          onLayoutAction={(a) => {
            if (a === 'save') {
              if (canRecordsWrite) void save();
              return;
            }
            navigate(`/entities/${entityId}/records`);
          }}
        />
      )}
    </div>
  );
}

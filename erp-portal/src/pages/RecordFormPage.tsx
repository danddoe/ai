import { Button } from '@mantine/core';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  createRecord,
  getEntity,
  getRecord,
  isDocumentNumberFieldType,
  listFields,
  listEntities,
  listFormLayouts,
  listRecordAuditEvents,
  patchRecord,
  type AuditEventDto,
  type EntityDto,
  type EntityFieldDto,
  type FormLayoutDto,
  type RecordDto,
} from '../api/schemas';
import { useAuth } from '../auth/AuthProvider';
import { AuditEventsTable } from '../components/audit/AuditEventsTable';
import { AuditPayloadModal } from '../components/AuditPayloadModal';
import { RecordFormRuntimeProvider } from '../components/runtime/RecordFormRuntimeContext';
import {
  buildInlineFieldErrorsForRegions,
  LayoutV2RuntimeRenderer,
} from '../components/runtime/LayoutV2RuntimeRenderer';
import { buildEntityBySlugForReferenceFields } from '../utils/referenceFieldConfig';
import {
  assertCoreDomainRoutedOrThrow,
  createCoreMasterRow,
  coreDtoToFormValues,
  getCoreMasterRow,
  isCoreServiceHybridEntitySlug,
  patchCoreMasterRow,
} from '../api/coreMasterDataHybrid';
import { isCoreDomainField } from '../utils/fieldStorage';
import { isActionItem, parseLayoutV2, regionsForWizardStep, resolveLayoutItemField } from '../utils/layoutV2';
import type { LayoutV2 } from '../types/formLayout';

function findFirstWizardStepWithFieldError(
  layout: LayoutV2,
  wizardRegionIds: string[],
  errs: Record<string, string>,
  flds: EntityFieldDto[]
): number | null {
  const bad = new Set(Object.keys(errs));
  if (bad.size === 0) return null;
  for (let si = 0; si < wizardRegionIds.length; si++) {
    const rid = wizardRegionIds[si];
    const region = layout.regions.find((r) => r.id === rid);
    if (!region) continue;
    for (const row of region.rows) {
      for (const col of row.columns) {
        for (const item of col.items) {
          if (isActionItem(item)) continue;
          const f = resolveLayoutItemField(item, flds);
          if (f && bad.has(f.slug)) return si;
        }
      }
    }
  }
  return null;
}

/** Locale date + local wall time with milliseconds (avoids Intl combos that throw in some runtimes). */
function shortUuid(id: string): string {
  return id.length > 10 ? `${id.slice(0, 8)}…` : id;
}

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

export function RecordFormPage() {
  const { entityId = '', recordId = '' } = useParams();
  const navigate = useNavigate();
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
  const [auditPayloadOpen, setAuditPayloadOpen] = useState<AuditEventDto | null>(null);
  const [entitySlug, setEntitySlug] = useState<string>('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [recordDetail, setRecordDetail] = useState<RecordDto | null>(null);
  const [allEntities, setAllEntities] = useState<EntityDto[]>([]);

  const entityBySlug = useMemo(
    () => buildEntityBySlugForReferenceFields(fields, allEntities),
    [fields, allEntities]
  );

  const wizardIds = layout?.runtime?.recordEntry?.wizard?.stepOrderRegionIds ?? [];
  const isWizard =
    layout?.runtime?.recordEntry?.flow === 'wizard' && wizardIds.length > 0;
  const wizardLast = isWizard && wizardStep >= wizardIds.length - 1;

  const regionsToRender = useMemo(() => {
    if (!layout) return [];
    if (isWizard) return regionsForWizardStep(layout, wizardStep);
    return layout.regions;
  }, [layout, isWizard, wizardStep]);

  const onAuditViewPayload = useCallback((e: AuditEventDto) => setAuditPayloadOpen(e), []);

  const load = useCallback(async () => {
    if (!tenantId || !entityId || !recordId) return;
    if (!canRecordsRead) {
      setLoading(false);
      return;
    }
    setError(null);
    setLoading(true);
    try {
      const [ent, flds, layouts, ents] = await Promise.all([
        getEntity(entityId),
        listFields(entityId),
        listFormLayouts(entityId),
        listEntities(),
      ]);
      setAllEntities(ents);
      setEntityName(ent.name);
      setEntitySlug(ent.slug || '');
      setFields(flds);
      const def = layouts.find((l) => l.isDefault) ?? null;
      setLayoutDto(def);
      if (!def) {
        setLayout(null);
        setValues({});
        setFieldErrors({});
        return;
      }
      const parsed = parseLayoutV2(def.layout);
      setLayout(parsed);
      if (!parsed) {
        setError('Default layout is not valid layout v2 JSON.');
        setValues({});
        setFieldErrors({});
        return;
      }

      if (isCreate) {
        setRecordDetail(null);
        setValues({});
        setWizardStep(0);
        setFieldErrors({});
        return;
      }

      const rec = await getRecord(tenantId, entityId, recordId);
      setRecordDetail(rec);
      let merged = { ...rec.values };
      if (isCoreServiceHybridEntitySlug(ent.slug) && rec.externalId) {
        try {
          const coreDto = await getCoreMasterRow(tenantId, ent.slug, rec.externalId);
          merged = { ...coreDtoToFormValues(flds, coreDto), ...merged };
        } catch (e) {
          throw new Error(
            e instanceof Error
              ? e.message
              : 'Failed to load core row for this record (check domain API permissions).'
          );
        }
      }
      setValues(merged);
      setWizardStep(0);
      setFieldErrors({});
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load');
      setLayout(null);
      setValues({});
      setFieldErrors({});
      setRecordDetail(null);
      setAllEntities([]);
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
    setFieldErrors((prev) => {
      if (!prev[slug]) return prev;
      const next = { ...prev };
      delete next[slug];
      return next;
    });
  }, []);

  function buildSubmitValues(raw: Record<string, unknown>): Record<string, unknown> {
    const out: Record<string, unknown> = {};
    for (const f of fields) {
      if (isCoreDomainField(f)) {
        continue;
      }
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
    const errs = buildInlineFieldErrorsForRegions(regionsToRender, fields, values);
    setFieldErrors(errs);
    return Object.keys(errs).length === 0;
  }

  function resetFormForAnotherRecord() {
    setValues({});
    setWizardStep(0);
    setFieldErrors({});
    setRecordDetail(null);
    setActiveTab('form');
  }

  async function save(opts?: { addAnother?: boolean }) {
    if (!tenantId || !layout || !canRecordsWrite) return;
    const errs = buildInlineFieldErrorsForRegions(layout.regions, fields, values);
    setFieldErrors(errs);
    if (Object.keys(errs).length > 0) {
      if (isWizard && wizardIds.length > 0) {
        const step = findFirstWizardStepWithFieldError(layout, wizardIds, errs, fields);
        if (step !== null) setWizardStep(step);
      }
      return;
    }
    const payload = buildSubmitValues(values);
    const bdn = businessDocumentNumberFromValues(values);
    setSaving(true);
    setError(null);
    setSaveSuccess(null);
    try {
      assertCoreDomainRoutedOrThrow(entitySlug, fields);
      const hybrid = isCoreServiceHybridEntitySlug(entitySlug);

      if (isCreate) {
        const addAnother = opts?.addAnother === true;
        if (hybrid) {
          const coreId = await createCoreMasterRow(tenantId, entitySlug, fields, values);
          const created = await createRecord(tenantId, entityId, {
            values: payload,
            externalId: coreId,
            ...(bdn !== undefined ? { businessDocumentNumber: bdn } : {}),
          });
          if (addAnother) {
            resetFormForAnotherRecord();
            setSaveSuccess(
              `Record saved (${shortUuid(created.id)}). You can add another below.`
            );
          } else {
            const savedAt = new Date().toISOString();
            navigate(`/entities/${entityId}/records/${created.id}`, {
              replace: true,
              state: { recordSaveFlash: { at: savedAt } },
            });
          }
        } else {
          const created = await createRecord(tenantId, entityId, {
            values: payload,
            ...(bdn !== undefined ? { businessDocumentNumber: bdn } : {}),
          });
          if (addAnother) {
            resetFormForAnotherRecord();
            setSaveSuccess(
              `Record saved (${shortUuid(created.id)}). You can add another below.`
            );
          } else {
            const savedAt = new Date().toISOString();
            navigate(`/entities/${entityId}/records/${created.id}`, {
              replace: true,
              state: { recordSaveFlash: { at: savedAt } },
            });
          }
        }
      } else if (hybrid) {
        const rec = await getRecord(tenantId, entityId, recordId);
        if (!rec.externalId) {
          throw new Error(
            'This extension record has no externalId link to the domain row; cannot save core fields. Recreate the record or fix data.'
          );
        }
        await patchCoreMasterRow(tenantId, entitySlug, rec.externalId, fields, values);
        await patchRecord(tenantId, entityId, recordId, { values: payload });
        await load();
        setFieldErrors({});
        setSaveSuccess(`Saved successfully at ${formatSavedTimestamp(new Date())}.`);
        void loadAuditHistory();
      } else {
        await patchRecord(tenantId, entityId, recordId, { values: payload });
        await load();
        setFieldErrors({});
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
          <Button component={Link} variant="default" to={`/entities/${entityId}/records`}>
            Back to list
          </Button>
          {!isCreate && (
            <Button component={Link} variant="default" to={`/entities/${entityId}/records/new`}>
              Add record
            </Button>
          )}
          {isWizard && wizardStep > 0 && (
            <Button
              type="button"
              variant="default"
              disabled={saving}
              onClick={() => {
                setFieldErrors({});
                setWizardStep((s) => Math.max(0, s - 1));
              }}
            >
              Back
            </Button>
          )}
          {isWizard && !wizardLast && (
            <Button
              type="button"
              disabled={saving}
              onClick={() => {
                if (!validateCurrentStep()) return;
                setWizardStep((s) => s + 1);
              }}
            >
              Next
            </Button>
          )}
          {canRecordsWrite && (!isWizard || wizardLast) && (
            <>
              <Button type="button" disabled={saving || !layout} onClick={() => void save()}>
                {saving ? 'Saving…' : 'Save'}
              </Button>
              {isCreate && (
                <Button
                  type="button"
                  variant="light"
                  disabled={saving || !layout}
                  onClick={() => void save({ addAnother: true })}
                >
                  {saving ? 'Saving…' : 'Save and add another'}
                </Button>
              )}
            </>
          )}
        </div>
      </header>
      {!isCreate && recordDetail && (
        <div
          className="builder-muted"
          style={{ fontSize: '0.8125rem', marginBottom: 14, lineHeight: 1.55 }}
        >
          <span>Created {new Date(recordDetail.createdAt).toLocaleString()}</span>
          <span aria-hidden> · </span>
          <span>
            Created by{' '}
            {recordDetail.createdByLabel?.trim() ? (
              <span title={recordDetail.createdBy ?? undefined}>{recordDetail.createdByLabel.trim()}</span>
            ) : recordDetail.createdBy ? (
              <code title={recordDetail.createdBy}>{shortUuid(recordDetail.createdBy)}</code>
            ) : (
              '—'
            )}
          </span>
          <span aria-hidden> · </span>
          <span>Last updated {new Date(recordDetail.updatedAt).toLocaleString()}</span>
          <span aria-hidden> · </span>
          <span>
            Last edited by{' '}
            {recordDetail.updatedByLabel?.trim() ? (
              <span title={recordDetail.updatedBy ?? undefined}>{recordDetail.updatedByLabel.trim()}</span>
            ) : recordDetail.updatedBy ? (
              <code title={recordDetail.updatedBy}>{shortUuid(recordDetail.updatedBy)}</code>
            ) : (
              '—'
            )}
          </span>
        </div>
      )}
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
          <Button
            type="button"
            role="tab"
            variant={activeTab === 'form' ? 'filled' : 'default'}
            aria-selected={activeTab === 'form'}
            onClick={() => setActiveTab('form')}
          >
            Form
          </Button>
          <Button
            type="button"
            role="tab"
            variant={activeTab === 'history' ? 'filled' : 'default'}
            aria-selected={activeTab === 'history'}
            onClick={() => setActiveTab('history')}
          >
            History
          </Button>
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
                <AuditEventsTable items={auditEvents} onViewPayload={onAuditViewPayload} />
              )}
            </>
          )}
        </section>
      )}
      {!loading && layout && activeTab === 'form' && (
        <RecordFormRuntimeProvider tenantId={tenantId ?? null} hostEntityId={entityId} entityBySlug={entityBySlug}>
          <LayoutV2RuntimeRenderer
            regions={regionsToRender}
            fields={fields}
            values={values}
            onChange={onFieldChange}
            disabled={!canRecordsWrite}
            canPiiRead={canPiiRead}
            useTabGroups={!isWizard}
            fieldErrors={fieldErrors}
            onLayoutAction={(a) => {
              if (a === 'save') {
                if (canRecordsWrite) void save();
                return;
              }
              navigate(`/entities/${entityId}/records`);
            }}
          />
        </RecordFormRuntimeProvider>
      )}
      {auditPayloadOpen && (
        <AuditPayloadModal
          payload={auditPayloadOpen.payload}
          title={`Payload · ${auditPayloadOpen.action} · ${new Date(auditPayloadOpen.createdAt).toLocaleString()}`}
          onClose={() => setAuditPayloadOpen(null)}
        />
      )}
    </div>
  );
}

import { Button } from '@mantine/core';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import {
  getEntity,
  getFormLayout,
  listEntities,
  listEntityRelationships,
  listFields,
  type EntityDto,
  type EntityFieldDto,
  type EntityRelationshipDto,
} from '../api/schemas';
import { useAuth } from '../auth/AuthProvider';
import { RecordFormRuntimeProvider } from '../components/runtime/RecordFormRuntimeContext';
import { LayoutV2RuntimeRenderer } from '../components/runtime/LayoutV2RuntimeRenderer';
import { buildEntityBySlugForReferenceFields } from '../utils/referenceFieldConfig';
import { parseLayoutV2, regionsForWizardStep } from '../utils/layoutV2';
import type { LayoutV2 } from '../types/formLayout';

export type FormLayoutPreviewLocationState = {
  draft?: LayoutV2;
};

function isLayoutV2Draft(x: unknown): x is LayoutV2 {
  if (!x || typeof x !== 'object') return false;
  const o = x as Record<string, unknown>;
  return Number(o.version) === 2 && Array.isArray(o.regions);
}

function buildSampleValues(fields: EntityFieldDto[]): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const f of fields) {
    const t = (f.fieldType || 'text').toLowerCase();
    if (t === 'number') out[f.slug] = 1234;
    else if (t === 'document_number') out[f.slug] = 'JV2026010001';
    else if (t === 'boolean') out[f.slug] = true;
    else if (t === 'date') out[f.slug] = '2024-06-01T12:00:00Z';
    else if (t === 'reference') out[f.slug] = '00000000-0000-4000-8000-000000000001';
    else out[f.slug] = `Sample: ${f.name}`;
  }
  return out;
}

export function FormLayoutPreviewPage() {
  const { entityId = '', layoutId = '' } = useParams();
  const location = useLocation();
  const { tenantId, canPiiRead } = useAuth();

  const [entity, setEntity] = useState<EntityDto | null>(null);
  const [layout, setLayout] = useState<LayoutV2 | null>(null);
  const [layoutName, setLayoutName] = useState('');
  const [fields, setFields] = useState<EntityFieldDto[]>([]);
  const [values, setValues] = useState<Record<string, unknown>>({});
  const [source, setSource] = useState<'draft' | 'saved' | null>(null);
  const [wizardStep, setWizardStep] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [allEntities, setAllEntities] = useState<EntityDto[]>([]);
  const [relationships, setRelationships] = useState<EntityRelationshipDto[]>([]);

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

  const load = useCallback(async () => {
    if (!entityId || !layoutId) return;
    setError(null);
    setLoading(true);
    const st = location.state as FormLayoutPreviewLocationState | null;
    const draftFromNav = st?.draft;
    if (isLayoutV2Draft(draftFromNav)) {
      try {
        const [e, flds, allEnts, rels] = await Promise.all([
          getEntity(entityId),
          listFields(entityId),
          listEntities(),
          listEntityRelationships(),
        ]);
        setEntity(e);
        setFields(flds);
        setAllEntities(allEnts);
        setRelationships(rels);
        setLayout(draftFromNav);
        setLayoutName('(unsaved draft)');
        setValues(buildSampleValues(flds));
        setSource('draft');
        setWizardStep(0);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to load');
        setLayout(null);
        setAllEntities([]);
        setRelationships([]);
      } finally {
        setLoading(false);
      }
      return;
    }

    try {
      const [e, flds, dto, allEnts, rels] = await Promise.all([
        getEntity(entityId),
        listFields(entityId),
        getFormLayout(entityId, layoutId),
        listEntities(),
        listEntityRelationships(),
      ]);
      setEntity(e);
      setFields(flds);
      setAllEntities(allEnts);
      setRelationships(rels);
      setLayoutName(dto.name);
      const parsed = parseLayoutV2(dto.layout);
      if (!parsed) {
        setError('Layout is not valid v2 JSON; nothing to preview.');
        setLayout(null);
      } else {
        setLayout(parsed);
        setValues(buildSampleValues(flds));
        setSource('saved');
        setWizardStep(0);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load');
      setLayout(null);
      setAllEntities([]);
      setRelationships([]);
    } finally {
      setLoading(false);
    }
  }, [entityId, layoutId, location.state]);

  useEffect(() => {
    void load();
  }, [load]);

  const onFieldChange = useCallback((slug: string, v: unknown) => {
    setValues((prev) => ({ ...prev, [slug]: v }));
  }, []);

  return (
    <div className="page-shell page-shell-wide">
      <div
        className="preview-banner"
        style={{
          padding: '10px 14px',
          marginBottom: 16,
          background: '#fef9c3',
          border: '1px solid #fde047',
          borderRadius: 8,
          fontSize: '0.875rem',
        }}
      >
        <strong>Preview</strong> — sample data only; nothing is saved to the server. Uses the same renderer as record
        entry{source === 'draft' ? ' (including unsaved editor changes).' : '.'}
      </div>

      <nav className="breadcrumb">
        <Link to="/entities">Entities</Link>
        <span aria-hidden> / </span>
        <Link to={`/entities/${entityId}/layouts`}>{entity?.name ?? '…'}</Link>
        <span aria-hidden> / </span>
        <Link to={`/entities/${entityId}/layouts/${layoutId}`}>{layoutName || 'Layout'}</Link>
        <span aria-hidden> / </span>
        <span>Preview</span>
      </nav>

      <header className="page-header">
        <div>
          <h1 className="page-title">Layout preview</h1>
          <p className="page-sub">
            {entity ? `${entity.name} · ${layoutName}` : 'Loading…'}
            {isWizard ? ` · Wizard step ${wizardStep + 1} of ${wizardIds.length}` : ''}
          </p>
        </div>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          <Button component={Link} variant="default" to={`/entities/${entityId}/layouts/${layoutId}`}>
            Back to builder
          </Button>
        </div>
      </header>

      {error && (
        <p role="alert" className="text-error">
          {error}
        </p>
      )}

      {loading && <p className="builder-muted">Loading…</p>}

      {!loading && layout && (
        <>
          {isWizard && (
            <div style={{ display: 'flex', gap: 8, marginBottom: 16, flexWrap: 'wrap' }}>
              <Button
                type="button"
                variant="default"
                size="sm"
                disabled={wizardStep <= 0}
                onClick={() => setWizardStep((s) => Math.max(0, s - 1))}
              >
                Back
              </Button>
              {!wizardLast && (
                <Button type="button" size="sm" onClick={() => setWizardStep((s) => s + 1)}>
                  Next
                </Button>
              )}
            </div>
          )}
          <RecordFormRuntimeProvider tenantId={tenantId ?? null} hostEntityId={entityId} entityBySlug={entityBySlug}>
            <LayoutV2RuntimeRenderer
              regions={regionsToRender}
              fields={fields}
              values={values}
              onChange={onFieldChange}
              disabled={false}
              canPiiRead={canPiiRead}
              useTabGroups={!isWizard}
              relatedContext={
                tenantId
                  ? {
                      tenantId,
                      hostEntityId: entityId,
                      parentRecordId: null,
                      relationships,
                      allEntities,
                      canWrite: false,
                    }
                  : undefined
              }
              onLayoutAction={(a) => {
                window.alert(
                  a === 'save'
                    ? 'Preview: Save runs on the real record form (not in preview).'
                    : 'Preview: Cancel would return to the record list on the real form.'
                );
              }}
            />
          </RecordFormRuntimeProvider>
        </>
      )}
    </div>
  );
}

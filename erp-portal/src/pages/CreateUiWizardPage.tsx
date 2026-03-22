import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  ApiHttpError,
  createNavigationItem,
  createRecordListView,
  listEntities,
  listFields,
  listFormLayouts,
  listRecordListViews,
  patchFormLayout,
  type EntityDto,
  type EntityFieldDto,
  type NavigationItemDto,
  type RecordListViewDto,
} from '../api/schemas';
import { useAuth } from '../auth/AuthProvider';
import { clearPortalNavigationCache, usePortalNavigation } from '../hooks/usePortalNavigation';
import type { LayoutV2 } from '../types/formLayout';
import {
  UI_TEMPLATES,
  type UiTemplateId,
  requiredPermissionsForTemplate,
  routePathForTemplate,
  searchKeywordsForEntity,
} from '../ui/uiTemplates';
import { parseLayoutV2 } from '../utils/layoutV2';
import { buildRecordListViewDefinitionV1 } from '../utils/recordListViewDefinition';
import { recordsListPath } from '../utils/recordsListNav';

function collectSectionParents(items: NavigationItemDto[]): { id: string; label: string }[] {
  const acc: { id: string; label: string }[] = [];
  function walk(nodes: NavigationItemDto[], depth: number) {
    for (const n of nodes) {
      if (n.type === 'section') {
        acc.push({ id: n.id, label: `${'\u00A0\u00A0'.repeat(depth)}${n.label}` });
      }
      if (n.children?.length) walk(n.children, depth + 1);
    }
  }
  walk(items, 0);
  return acc;
}

function columnStepEnabled(template: UiTemplateId): boolean {
  return template === 'list_only' || template === 'list_and_form' || template === 'multi_step';
}

async function applyMultiStepWizard(entityId: string): Promise<void> {
  const layouts = await listFormLayouts(entityId);
  const def = layouts.find((l) => l.isDefault);
  if (!def) {
    throw new Error('Mark a default form layout for this entity before using the multi-step template.');
  }
  const parsed = parseLayoutV2(def.layout);
  if (!parsed) {
    throw new Error('Default layout is not valid v2 JSON.');
  }
  const stepOrderRegionIds = parsed.regions.map((r) => r.id);
  const layout: LayoutV2 = {
    ...parsed,
    runtime: {
      recordEntry: {
        flow: 'wizard',
        wizard: { stepOrderRegionIds },
      },
    },
  };
  await patchFormLayout(entityId, def.id, { layout });
}

const ENTITY_SEARCH_DEBOUNCE_MS = 320;

export function CreateUiWizardPage() {
  const navigate = useNavigate();
  const { canCreatePortalNavItem, canManageGlobalNavigation, canSchemaWrite } = useAuth();
  const { items: navRoots, load: loadNav, state: navState } = usePortalNavigation();

  const [step, setStep] = useState(1);
  const [template, setTemplate] = useState<UiTemplateId>('list_and_form');
  const [entities, setEntities] = useState<EntityDto[] | null>(null);
  const [entitiesLoading, setEntitiesLoading] = useState(false);
  const [entitySearchInput, setEntitySearchInput] = useState('');
  const [entitySearchDebounced, setEntitySearchDebounced] = useState('');
  const [entityId, setEntityId] = useState<string | null>(null);
  /** Keeps display metadata when the current search result set no longer includes the selected id. */
  const [pickedEntity, setPickedEntity] = useState<EntityDto | null>(null);
  const [label, setLabel] = useState('');
  const [description, setDescription] = useState('');
  const [parentId, setParentId] = useState<string>('');
  const [globalScope, setGlobalScope] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const [listFieldsState, setListFieldsState] = useState<EntityFieldDto[] | null>(null);
  const [listFieldsErr, setListFieldsErr] = useState<string | null>(null);
  const [fieldSearch, setFieldSearch] = useState('');
  const [selectedColSlugs, setSelectedColSlugs] = useState<string[]>([]);
  const [inlineColSlugs, setInlineColSlugs] = useState<string[]>([]);
  const [showRowActions, setShowRowActions] = useState(true);

  type ListMode = 'saved' | 'legacy' | 'create_saved';
  const [listMode, setListMode] = useState<ListMode>('legacy');
  const [savedViews, setSavedViews] = useState<RecordListViewDto[] | null>(null);
  const [savedViewsErr, setSavedViewsErr] = useState<string | null>(null);
  const [savedViewId, setSavedViewId] = useState<string>('');
  const [newListViewName, setNewListViewName] = useState('');

  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const colStep = columnStepEnabled(template);
  const navStepNum = colStep ? 4 : 3;
  const reviewStepNum = colStep ? 5 : 4;

  const sectionParents = useMemo(() => collectSectionParents(navRoots), [navRoots]);

  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => {
      setEntitySearchDebounced(entitySearchInput.trim());
    }, ENTITY_SEARCH_DEBOUNCE_MS);
    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current);
    };
  }, [entitySearchInput]);

  const loadEntities = useCallback(async () => {
    setEntitiesLoading(true);
    try {
      const q = entitySearchDebounced;
      setEntities(await listEntities(q ? { q } : undefined));
      setError(null);
    } catch (e) {
      let msg = e instanceof Error ? e.message : 'Failed to load entities';
      if (e instanceof ApiHttpError && e.status === 403) {
        msg = `${msg} You need entity_builder:schema:read or entity_builder:schema:write (or portal:navigation:write for listing only).`;
      }
      setError(msg);
      setEntities([]);
    } finally {
      setEntitiesLoading(false);
    }
  }, [entitySearchDebounced]);

  useEffect(() => {
    void loadEntities();
  }, [loadEntities]);

  useEffect(() => {
    if (navState.status === 'idle') void loadNav();
  }, [navState.status, loadNav]);

  const selectedEntity = useMemo(() => {
    if (!entityId) return null;
    const fromList = entities?.find((e) => e.id === entityId);
    if (fromList) return fromList;
    if (pickedEntity?.id === entityId) return pickedEntity;
    return null;
  }, [entities, entityId, pickedEntity]);

  useEffect(() => {
    if (selectedEntity && !label.trim()) {
      setLabel(selectedEntity.name);
    }
  }, [selectedEntity, label]);

  useEffect(() => {
    setSelectedColSlugs([]);
    setInlineColSlugs([]);
    setShowRowActions(true);
    setFieldSearch('');
    setListFieldsState(null);
    setListMode('legacy');
    setSavedViewId('');
    setNewListViewName('');
    setSavedViews(null);
  }, [entityId]);

  useEffect(() => {
    if (!colStep) {
      setSelectedColSlugs([]);
      setInlineColSlugs([]);
      setListFieldsState(null);
    }
  }, [template, colStep]);

  useEffect(() => {
    if (step !== 3 || !colStep || !entityId) return;
    let cancelled = false;
    void (async () => {
      try {
        setListFieldsErr(null);
        const fields = await listFields(entityId);
        if (!cancelled) setListFieldsState(fields);
      } catch (e) {
        if (!cancelled) {
          setListFieldsErr(e instanceof Error ? e.message : 'Failed to load fields');
          setListFieldsState([]);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [step, colStep, entityId]);

  useEffect(() => {
    if (step !== 3 || !colStep || !entityId || !canSchemaWrite) return;
    let cancelled = false;
    void (async () => {
      try {
        setSavedViewsErr(null);
        const v = await listRecordListViews(entityId);
        if (!cancelled) setSavedViews(v);
      } catch (e) {
        if (!cancelled) {
          setSavedViewsErr(e instanceof Error ? e.message : 'Failed to load list views');
          setSavedViews([]);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [step, colStep, entityId, canSchemaWrite]);

  const previewPath = useMemo(() => {
    if (!entityId) return '';
    if (!colStep) return routePathForTemplate(template, entityId);
    if (listMode === 'saved' && savedViewId.trim()) {
      return recordsListPath(entityId, {
        viewId: savedViewId.trim(),
        cols: [],
        inlineSlugs: [],
        showRowActions: true,
      });
    }
    return recordsListPath(entityId, {
      viewId: null,
      cols: selectedColSlugs,
      inlineSlugs: inlineColSlugs,
      showRowActions,
    });
  }, [entityId, template, colStep, listMode, savedViewId, selectedColSlugs, inlineColSlugs, showRowActions]);

  const previewPerms = requiredPermissionsForTemplate(template);

  const filteredFields = useMemo(() => {
    if (!listFieldsState) return [];
    const q = fieldSearch.trim().toLowerCase();
    if (!q) return listFieldsState;
    return listFieldsState.filter(
      (f) => f.name.toLowerCase().includes(q) || f.slug.toLowerCase().includes(q) || f.fieldType.toLowerCase().includes(q)
    );
  }, [listFieldsState, fieldSearch]);

  function toggleColumn(slug: string) {
    setSelectedColSlugs((prev) => (prev.includes(slug) ? prev.filter((s) => s !== slug) : [...prev, slug]));
    setInlineColSlugs((prev) => prev.filter((s) => s !== slug));
  }

  function toggleInline(slug: string) {
    if (!selectedColSlugs.includes(slug)) return;
    setInlineColSlugs((prev) => (prev.includes(slug) ? prev.filter((s) => s !== slug) : [...prev, slug]));
  }

  async function submit() {
    if (!entityId || !label.trim() || !previewPath) return;
    setBusy(true);
    setError(null);
    try {
      if (template === 'multi_step') {
        await applyMultiStepWizard(entityId);
      }
      let routePath = previewPath;
      if (colStep && listMode === 'create_saved') {
        const name = newListViewName.trim();
        if (!name) throw new Error('Enter a name for the new list view.');
        if (selectedColSlugs.length === 0) throw new Error('Pick at least one column for the new list view.');
        const def = buildRecordListViewDefinitionV1(
          selectedColSlugs.map((slug, i) => ({
            fieldSlug: slug,
            order: i,
            inlineEditable: inlineColSlugs.includes(slug),
            visible: true,
          })),
          showRowActions
        );
        const created = await createRecordListView(entityId, {
          name,
          definition: def as unknown as Record<string, unknown>,
          isDefault: false,
        });
        routePath = recordsListPath(entityId, {
          viewId: created.id,
          cols: [],
          inlineSlugs: [],
          showRowActions: true,
        });
      }
      await createNavigationItem({
        parentId: parentId || null,
        sortOrder: 50,
        routePath,
        label: label.trim(),
        description: description.trim() || null,
        type: 'internal',
        icon: 'layout-list',
        categoryKey: 'entity_builder',
        searchKeywords: searchKeywordsForEntity(selectedEntity?.name ?? '', selectedEntity?.slug ?? ''),
        requiredPermissions: previewPerms,
        requiredRoles: [],
        scope: globalScope && canManageGlobalNavigation ? 'GLOBAL' : 'TENANT',
      });
      clearPortalNavigationCache();
      await loadNav();
      navigate(routePath);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Create failed');
    } finally {
      setBusy(false);
    }
  }

  if (!canCreatePortalNavItem) {
    return (
      <div className="page-shell">
        <p role="alert" className="text-error">
          You need <code>portal:navigation:write</code> or <code>entity_builder:schema:write</code> to create portal
          navigation.
        </p>
        <Link to="/home">← Home</Link>
      </div>
    );
  }

  return (
    <div className="page-shell page-shell-wide">
      <nav className="breadcrumb">
        <Link to="/home">Home</Link>
        <span aria-hidden> / </span>
        <span>Create UI</span>
      </nav>
      <header className="page-header">
        <div>
          <h1 className="page-title">Create UI</h1>
          <p className="page-sub">Template, entity, list columns (for list routes), then register a sidebar link.</p>
        </div>
      </header>

      {error && (
        <p role="alert" className="text-error">
          {error}
        </p>
      )}

      <div className="wizard-steps" style={{ marginBottom: 16 }}>
        <span className={step >= 1 ? 'wizard-step-on' : ''}>1. Template</span>
        {' · '}
        <span className={step >= 2 ? 'wizard-step-on' : ''}>2. Entity</span>
        {colStep && (
          <>
            {' · '}
            <span className={step >= 3 ? 'wizard-step-on' : ''}>3. List columns</span>
          </>
        )}
        {' · '}
        <span className={step >= navStepNum ? 'wizard-step-on' : ''}>{colStep ? '4' : '3'}. Navigation</span>
        {' · '}
        <span className={step >= reviewStepNum ? 'wizard-step-on' : ''}>{colStep ? '5' : '4'}. Review</span>
      </div>

      {step === 1 && (
        <section>
          <h2 className="page-title" style={{ fontSize: '1.1rem' }}>
            Choose template
          </h2>
          <ul style={{ listStyle: 'none', padding: 0, display: 'grid', gap: 10 }}>
            {UI_TEMPLATES.map((t) => (
              <li key={t.id}>
                <label className="entity-card" style={{ cursor: 'pointer', display: 'block' }}>
                  <input
                    type="radio"
                    name="tpl"
                    checked={template === t.id}
                    onChange={() => setTemplate(t.id)}
                    style={{ marginRight: 8 }}
                  />
                  <strong>{t.title}</strong>
                  <div className="builder-muted" style={{ marginTop: 4 }}>
                    {t.description}
                  </div>
                </label>
              </li>
            ))}
          </ul>
          <button type="button" className="btn btn-primary" style={{ marginTop: 16 }} onClick={() => setStep(2)}>
            Next
          </button>
        </section>
      )}

      {step === 2 && (
        <section>
          <h2 className="page-title" style={{ fontSize: '1.1rem' }}>
            Choose entity
          </h2>
          <input
            className="input"
            placeholder="Search by name, slug, or id…"
            value={entitySearchInput}
            onChange={(e) => setEntitySearchInput(e.target.value)}
            style={{ maxWidth: 420, marginBottom: 12 }}
          />
          {entitiesLoading && <p className="builder-muted">Loading entities…</p>}
          <ul className="entity-list" style={{ maxHeight: 320, overflowY: 'auto' }}>
            {!entitiesLoading &&
              entities?.map((e) => (
                <li key={e.id}>
                  <label className="entity-card" style={{ cursor: 'pointer' }}>
                    <input
                      type="radio"
                      name="ent"
                      checked={entityId === e.id}
                      onChange={() => {
                        setEntityId(e.id);
                        setPickedEntity(e);
                      }}
                      style={{ marginRight: 8 }}
                    />
                    <span className="entity-card-name">{e.name}</span>
                    <code className="entity-card-slug">{e.slug}</code>
                  </label>
                </li>
              ))}
          </ul>
          {!entitiesLoading && entities && entities.length === 0 && !entitySearchDebounced && (
            <p className="builder-muted" style={{ marginTop: 8, maxWidth: 520 }}>
              No entities for this tenant. Bundled definitions (system catalog) are not loaded until someone runs{' '}
              <strong>Sync system catalog</strong> on the{' '}
              <Link to="/entities">Entities</Link> page (requires entity_builder:schema:write).
            </p>
          )}
          {entityId && !entities?.some((e) => e.id === entityId) && pickedEntity && (
            <p className="builder-muted" style={{ fontSize: '0.875rem' }}>
              Selected: <strong>{pickedEntity.name}</strong> — refine search to see it in the list.
            </p>
          )}
          <div style={{ marginTop: 16, display: 'flex', gap: 8 }}>
            <button type="button" className="btn btn-secondary" onClick={() => setStep(1)}>
              Back
            </button>
            <button type="button" className="btn btn-primary" disabled={!entityId} onClick={() => setStep(3)}>
              Next
            </button>
          </div>
        </section>
      )}

      {step === 3 && colStep && (
        <section>
          <h2 className="page-title" style={{ fontSize: '1.1rem' }}>
            List columns
          </h2>
          <p className="builder-muted" style={{ marginBottom: 12 }}>
            Use a saved list view (recommended), define URL-style columns, or create a new saved view and link it with{' '}
            <code>?view=</code>.
          </p>
          {canSchemaWrite && (
            <div style={{ display: 'grid', gap: 8, marginBottom: 16 }}>
              <label className="row" style={{ cursor: 'pointer' }}>
                <input
                  type="radio"
                  name="listMode"
                  checked={listMode === 'saved'}
                  onChange={() => setListMode('saved')}
                />
                <span>
                  <strong>Saved list view</strong> — pick an existing definition
                </span>
              </label>
              <label className="row" style={{ cursor: 'pointer' }}>
                <input
                  type="radio"
                  name="listMode"
                  checked={listMode === 'legacy'}
                  onChange={() => setListMode('legacy')}
                />
                <span>
                  <strong>URL columns (legacy)</strong> — <code>cols</code> / <code>inline</code> query params
                </span>
              </label>
              <label className="row" style={{ cursor: 'pointer' }}>
                <input
                  type="radio"
                  name="listMode"
                  checked={listMode === 'create_saved'}
                  onChange={() => setListMode('create_saved')}
                />
                <span>
                  <strong>Create new saved view</strong> — name + columns, then save to entity-builder on finish
                </span>
              </label>
            </div>
          )}
          {listMode === 'saved' && canSchemaWrite && (
            <div style={{ marginBottom: 16 }}>
              {savedViewsErr && (
                <p role="alert" className="text-error">
                  {savedViewsErr}
                </p>
              )}
              <label className="field-label">
                List view
                <select className="input" value={savedViewId} onChange={(e) => setSavedViewId(e.target.value)}>
                  <option value="">— Select —</option>
                  {(savedViews ?? []).map((v) => (
                    <option key={v.id} value={v.id}>
                      {v.name}
                      {v.isDefault ? ' (default)' : ''}
                    </option>
                  ))}
                </select>
              </label>
              {savedViews && savedViews.length === 0 && (
                <p className="builder-muted" style={{ fontSize: '0.875rem' }}>
                  No saved views yet — use “Create new saved view” or the records page designer.
                </p>
              )}
            </div>
          )}
          {listMode === 'create_saved' && canSchemaWrite && (
            <label className="field-label" style={{ marginBottom: 16, maxWidth: 420 }}>
              New view name
              <input
                className="input"
                value={newListViewName}
                onChange={(e) => setNewListViewName(e.target.value)}
                placeholder="e.g. Sales pipeline"
              />
            </label>
          )}
          {(listMode === 'legacy' || listMode === 'create_saved' || !canSchemaWrite) && (
            <>
              <p className="builder-muted" style={{ marginBottom: 12 }}>
                {listMode === 'legacy' && !canSchemaWrite
                  ? 'Pick fields as table columns (saved views require schema write permission).'
                  : 'Pick fields as table columns. Leave none selected for the default list (Id, display, updated).'}
              </p>
              {listFieldsErr && (
                <p role="alert" className="text-error">
                  {listFieldsErr}
                </p>
              )}
              <input
                className="input"
                placeholder="Filter fields…"
                value={fieldSearch}
                onChange={(e) => setFieldSearch(e.target.value)}
                style={{ maxWidth: 420, marginBottom: 12 }}
              />
              <label className="field-label row" style={{ marginBottom: 12 }}>
                <input type="checkbox" checked={showRowActions} onChange={(e) => setShowRowActions(e.target.checked)} />
                <span>Show row actions (Edit / Delete)</span>
              </label>
              <ul className="entity-list" style={{ maxHeight: 280, overflowY: 'auto' }}>
                {filteredFields.map((f) => (
                  <li key={f.id}>
                    <div className="entity-card" style={{ display: 'grid', gap: 6 }}>
                      <label className="row" style={{ cursor: 'pointer', margin: 0 }}>
                        <input
                          type="checkbox"
                          checked={selectedColSlugs.includes(f.slug)}
                          onChange={() => toggleColumn(f.slug)}
                        />
                        <span>
                          <strong>{f.name}</strong> <code className="entity-card-slug">{f.slug}</code>{' '}
                          <span className="builder-muted">({f.fieldType})</span>
                        </span>
                      </label>
                      {selectedColSlugs.includes(f.slug) && (
                        <label className="row" style={{ cursor: 'pointer', margin: '0 0 0 1.5rem', fontSize: '0.875rem' }}>
                          <input
                            type="checkbox"
                            checked={inlineColSlugs.includes(f.slug)}
                            onChange={() => toggleInline(f.slug)}
                          />
                          <span>Inline edit in list</span>
                        </label>
                      )}
                    </div>
                  </li>
                ))}
              </ul>
              {listFieldsState && listFieldsState.length === 0 && <p className="builder-muted">No fields on this entity.</p>}
            </>
          )}
          <div style={{ marginTop: 16, display: 'flex', gap: 8 }}>
            <button type="button" className="btn btn-secondary" onClick={() => setStep(2)}>
              Back
            </button>
            <button
              type="button"
              className="btn btn-primary"
              disabled={
                listMode === 'saved' && canSchemaWrite ? !savedViewId.trim() : false
              }
              onClick={() => setStep(4)}
            >
              Next
            </button>
          </div>
        </section>
      )}

      {step === navStepNum && (
        <section>
          <h2 className="page-title" style={{ fontSize: '1.1rem' }}>
            Navigation entry
          </h2>
          <div style={{ display: 'grid', gap: 12, maxWidth: 480 }}>
            <label className="field-label">
              Label
              <input className="input" value={label} onChange={(e) => setLabel(e.target.value)} />
            </label>
            <label className="field-label">
              Description (optional)
              <input className="input" value={description} onChange={(e) => setDescription(e.target.value)} />
            </label>
            <label className="field-label">
              Parent (section)
              <select className="input" value={parentId} onChange={(e) => setParentId(e.target.value)}>
                <option value="">— None —</option>
                {sectionParents.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.label}
                  </option>
                ))}
              </select>
            </label>
            {canManageGlobalNavigation && (
              <label className="field-label row">
                <input
                  type="checkbox"
                  checked={globalScope}
                  onChange={(e) => setGlobalScope(e.target.checked)}
                />
                <span>Global navigation (all tenants, subject to permissions)</span>
              </label>
            )}
          </div>
          <div style={{ marginTop: 16, display: 'flex', gap: 8 }}>
            <button type="button" className="btn btn-secondary" onClick={() => setStep(colStep ? 3 : 2)}>
              Back
            </button>
            <button
              type="button"
              className="btn btn-primary"
              disabled={!label.trim()}
              onClick={() => setStep(reviewStepNum)}
            >
              Next
            </button>
          </div>
        </section>
      )}

      {step === reviewStepNum && (
        <section>
          <h2 className="page-title" style={{ fontSize: '1.1rem' }}>
            Review
          </h2>
          <ul className="builder-muted" style={{ fontSize: '0.9rem' }}>
            <li>
              <strong>Template:</strong> {UI_TEMPLATES.find((t) => t.id === template)?.title}
            </li>
            <li>
              <strong>Entity:</strong> {selectedEntity?.name} ({entityId})
            </li>
            <li>
              <strong>Route:</strong> <code>{previewPath}</code>
            </li>
            {colStep && (
              <li>
                <strong>List:</strong>{' '}
                {listMode === 'saved' && savedViewId
                  ? `Saved view (${savedViewId})`
                  : listMode === 'create_saved'
                    ? `New saved view “${newListViewName.trim() || '…'}”`
                    : 'URL columns (legacy)'}
              </li>
            )}
            <li>
              <strong>Permissions:</strong> {previewPerms.join(', ')}
            </li>
            <li>
              <strong>Scope:</strong> {globalScope && canManageGlobalNavigation ? 'GLOBAL' : 'This tenant only'}
            </li>
            {template === 'multi_step' && (
              <li>
                <strong>Layout:</strong> default layout will be patched to wizard mode (region order).
              </li>
            )}
          </ul>
          <div style={{ marginTop: 16, display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            <button type="button" className="btn btn-secondary" onClick={() => setStep(navStepNum)} disabled={busy}>
              Back
            </button>
            <button type="button" className="btn btn-primary" disabled={busy} onClick={() => void submit()}>
              {busy ? 'Creating…' : 'Create & open'}
            </button>
          </div>
        </section>
      )}

      <p className="builder-muted" style={{ marginTop: 24 }}>
        <Link to="/entities">← Entities</Link>
      </p>
    </div>
  );
}

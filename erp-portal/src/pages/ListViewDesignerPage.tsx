import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import {
  createRecordListView,
  getEntity,
  getRecordListView,
  listFields,
  listRecordListViews,
  patchNavigationItem,
  patchRecordListView,
  type EntityDto,
  type EntityFieldDto,
  type RecordListViewDto,
} from '../api/schemas';
import { useAuth } from '../auth/AuthProvider';
import { CreateFieldModal } from '../components/builder/CreateFieldModal';
import { EditFieldModal } from '../components/builder/EditFieldModal';
import { ListDesignerFieldsPanel } from '../components/builder/ListDesignerFieldsPanel';
import { clearPortalNavigationCache, usePortalNavigation } from '../hooks/usePortalNavigation';
import {
  buildRecordListViewDefinitionV1,
  parseRecordListViewDefinition,
  type RecordListColumnDefinition,
} from '../utils/recordListViewDefinition';
import {
  mergeRecordsListLocation,
  navItemsForEntityRecordsList,
  parseListViewQueryFromRoutePath,
  recordsListPath,
  type ListViewQueryState,
} from '../utils/recordsListNav';

function normalizeOrders(cols: RecordListColumnDefinition[]): RecordListColumnDefinition[] {
  return cols.map((c, i) => ({ ...c, order: i }));
}

function legacyColumnsFromQuery(cols: string[], inlineSlugs: string[]): RecordListColumnDefinition[] {
  return cols.map((slug, i) => ({
    fieldSlug: slug,
    order: i,
    inlineEditable: inlineSlugs.includes(slug),
    visible: true,
  }));
}

export function ListViewDesignerPage() {
  const { entityId = '', viewId = '' } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const { items: navRoots, refresh } = usePortalNavigation();
  const { canSchemaWrite, canCreatePortalNavItem } = useAuth();

  const isNew = viewId === 'new';
  const candidates = useMemo(() => navItemsForEntityRecordsList(navRoots, entityId), [navRoots, entityId]);

  const [entity, setEntity] = useState<EntityDto | null>(null);
  const [fields, setFields] = useState<EntityFieldDto[]>([]);
  const [views, setViews] = useState<RecordListViewDto[]>([]);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const [navItemId, setNavItemId] = useState('');
  const [fieldSearch, setFieldSearch] = useState('');
  const [fieldFilter, setFieldFilter] = useState<'all' | 'on' | 'off'>('all');

  const [workingViewId, setWorkingViewId] = useState<string | null>(null);
  const [viewName, setViewName] = useState('');
  const [columns, setColumns] = useState<RecordListColumnDefinition[]>([]);
  const [selectedIdx, setSelectedIdx] = useState<number | null>(null);
  const [showRowActions, setShowRowActions] = useState(true);
  /** When true, this view is the entity default for /records without ?view=. */
  const [viewIsDefault, setViewIsDefault] = useState(false);

  const [createFieldOpen, setCreateFieldOpen] = useState(false);
  const [editField, setEditField] = useState<EntityFieldDto | null>(null);

  const listState = useMemo((): ListViewQueryState => {
    if (workingViewId) {
      return {
        viewId: workingViewId,
        cols: [],
        inlineSlugs: [],
        showRowActions: true,
      };
    }
    const ordered = normalizeOrders(columns);
    return {
      viewId: null,
      cols: ordered.map((c) => c.fieldSlug),
      inlineSlugs: ordered.filter((c) => c.inlineEditable).map((c) => c.fieldSlug),
      showRowActions,
    };
  }, [workingViewId, columns, showRowActions]);

  const recordsPreviewPath = useMemo(() => recordsListPath(entityId, listState), [entityId, listState]);

  const load = useCallback(async () => {
    if (!entityId || !viewId) return;
    setLoadError(null);
    try {
      const [e, f, vlist] = await Promise.all([
        getEntity(entityId),
        listFields(entityId),
        listRecordListViews(entityId),
      ]);
      setEntity(e);
      setFields([...f].sort((a, b) => a.name.localeCompare(b.name)));
      setViews(vlist);

      if (isNew) {
        setWorkingViewId(null);
        setViewName('');
        setColumns([]);
        setSelectedIdx(null);
        setShowRowActions(true);
        setViewIsDefault(false);
        const fromQs = (location.state as { fromRecordsQuery?: string } | null)?.fromRecordsQuery;
        if (fromQs) {
          const parsed = parseListViewQueryFromRoutePath(`?${fromQs}`);
          setShowRowActions(parsed.showRowActions);
          setColumns(normalizeOrders(legacyColumnsFromQuery(parsed.cols, parsed.inlineSlugs)));
          if (parsed.viewId && canSchemaWrite) {
            try {
              const dto = await getRecordListView(entityId, parsed.viewId);
              const def = parseRecordListViewDefinition(dto.definition);
              if (def) {
                setWorkingViewId(parsed.viewId);
                setViewName(dto.name);
                setColumns(normalizeOrders(def.columns));
                setShowRowActions(def.showRowActions !== false);
                setViewIsDefault(dto.isDefault);
                navigate(`/entities/${entityId}/list-views/${parsed.viewId}`, { replace: true });
              }
            } catch {
              /* keep legacy cols from URL */
            }
          }
        }
      } else {
        const dto = await getRecordListView(entityId, viewId);
        const def = parseRecordListViewDefinition(dto.definition);
        if (!def) {
          setLoadError('This list view is not valid version 1 JSON.');
          setColumns([]);
          setViewIsDefault(false);
        } else {
          setWorkingViewId(dto.id);
          setViewName(dto.name);
          setColumns(normalizeOrders(def.columns));
          setShowRowActions(def.showRowActions !== false);
          setViewIsDefault(dto.isDefault);
        }
      }
    } catch (err) {
      setLoadError(err instanceof Error ? err.message : 'Failed to load');
      setEntity(null);
      setFields([]);
      setViews([]);
      setViewIsDefault(false);
    }
  }, [entityId, viewId, isNew, navigate, location.key, canSchemaWrite]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (candidates.length === 0) {
      setNavItemId('');
    } else if (candidates.length === 1) {
      setNavItemId(String(candidates[0].id));
    }
  }, [candidates]);

  const selectedCol = selectedIdx !== null && selectedIdx >= 0 ? columns[selectedIdx] : null;

  function addColumn(slug: string) {
    if (!canSchemaWrite) return;
    if (columns.some((c) => c.fieldSlug === slug)) return;
    setColumns((prev) =>
      normalizeOrders([
        ...prev,
        { fieldSlug: slug, order: prev.length, visible: true, inlineEditable: false, linkToRecord: false },
      ])
    );
  }

  function removeColumn(idx: number) {
    if (!canSchemaWrite) return;
    setColumns((prev) => normalizeOrders(prev.filter((_, i) => i !== idx)));
    setSelectedIdx((s) => (s === null ? null : s === idx ? null : s > idx ? s - 1 : s));
  }

  function moveColumn(idx: number, dir: -1 | 1) {
    if (!canSchemaWrite) return;
    const j = idx + dir;
    if (j < 0 || j >= columns.length) return;
    setColumns((prev) => {
      const next = [...prev];
      const t = next[idx];
      next[idx] = next[j];
      next[j] = t;
      return normalizeOrders(next);
    });
    setSelectedIdx(j);
  }

  function updateSelected(patch: Partial<RecordListColumnDefinition>) {
    if (!canSchemaWrite || selectedIdx === null) return;
    setColumns((prev) => {
      const next = [...prev];
      const cur = next[selectedIdx];
      if (!cur) return prev;
      next[selectedIdx] = { ...cur, ...patch };
      return next;
    });
  }

  function onSwitchView(nextId: string) {
    if (nextId === 'new') {
      navigate(`/entities/${entityId}/list-views/new`, { state: location.state });
    } else {
      navigate(`/entities/${entityId}/list-views/${nextId}`);
    }
  }

  async function saveView() {
    if (!canSchemaWrite) return;
    const name = viewName.trim();
    if (!name) {
      setError('Enter a view name.');
      return;
    }
    if (columns.length === 0) {
      setError('Add at least one column.');
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const def = buildRecordListViewDefinitionV1(normalizeOrders(columns), showRowActions);
      const body = {
        name,
        definition: def as unknown as Record<string, unknown>,
        isDefault: viewIsDefault,
      };
      let savedId = workingViewId;
      if (workingViewId) {
        await patchRecordListView(entityId, workingViewId, body);
      } else {
        const created = await createRecordListView(entityId, body);
        savedId = created.id;
        setWorkingViewId(created.id);
        navigate(`/entities/${entityId}/list-views/${created.id}`, { replace: true });
      }
      const refreshed = await listRecordListViews(entityId);
      setViews(refreshed);
      const self = savedId ? refreshed.find((v) => v.id === savedId) : undefined;
      if (self) setViewIsDefault(self.isDefault);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Save failed');
    } finally {
      setSaving(false);
    }
  }

  const saveTargetId =
    candidates.length === 1 ? String(candidates[0].id) : navItemId.trim();
  const canSaveNav = canCreatePortalNavItem && candidates.length > 0 && saveTargetId.length > 0;

  async function saveToSidebar() {
    if (!canSaveNav) {
      setError('Select a sidebar link to update, or add portal navigation write permission.');
      return;
    }
    if (!workingViewId && columns.length === 0) {
      setError('Add columns or save the list view first (or use legacy URL columns).');
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const path = recordsListPath(entityId, listState);
      await patchNavigationItem(saveTargetId, { routePath: path });
      clearPortalNavigationCache();
      refresh();
      navigate(mergeRecordsListLocation(entityId, listState, new URLSearchParams()));
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Save failed');
    } finally {
      setSaving(false);
    }
  }

  function loadFromRecordsUrl() {
    const raw = window.prompt('Paste records query string (e.g. cols=a,b&inline=a) or full path query:', '');
    if (raw == null) return;
    const qs = raw.includes('?') ? raw.split('?')[1] ?? raw : raw;
    const parsed = parseListViewQueryFromRoutePath(`?${qs}`);
    setShowRowActions(parsed.showRowActions);
    if (parsed.viewId && canSchemaWrite) {
      const vid = parsed.viewId;
      void (async () => {
        try {
          const dto = await getRecordListView(entityId, vid);
          const def = parseRecordListViewDefinition(dto.definition);
          if (def) {
            setWorkingViewId(vid);
            setViewName(dto.name);
            setColumns(normalizeOrders(def.columns));
            setShowRowActions(def.showRowActions !== false);
            setViewIsDefault(dto.isDefault);
            navigate(`/entities/${entityId}/list-views/${vid}`, { replace: true });
          }
        } catch {
          setError('Could not load view from URL');
        }
      })();
    } else {
      setWorkingViewId(null);
      setViewName('');
      setViewIsDefault(false);
      setColumns(normalizeOrders(legacyColumnsFromQuery(parsed.cols, parsed.inlineSlugs)));
    }
  }

  if (loadError && !entity) {
    return (
      <div className="page-shell">
        <p role="alert" className="text-error">
          {loadError}
        </p>
        <Link to={`/entities/${entityId}/layouts`}>← Layouts</Link>
      </div>
    );
  }

  if (!entity) {
    return (
      <div className="page-shell">
        <p className="builder-muted">Loading…</p>
      </div>
    );
  }

  return (
    <div className="builder-page">
      <header className="builder-topbar">
        <nav className="breadcrumb builder-breadcrumb">
          <Link to="/entities">Entities</Link>
          <span aria-hidden> / </span>
          <Link to={`/entities/${entityId}/layouts`}>{entity.name}</Link>
          <span aria-hidden> / </span>
          <Link to={`/entities/${entityId}/records`}>Records</Link>
          <span aria-hidden> / </span>
          <span className="builder-muted">{isNew ? 'New list view' : viewName || 'List view'}</span>
        </nav>
        <div className="builder-topbar-actions">
          <span className="env-badge" title="API base">
            {import.meta.env.VITE_API_BASE_URL || 'http://localhost:8000'}
          </span>
          <Link className="btn btn-secondary btn-sm" to={recordsPreviewPath}>
            Open records (preview)
          </Link>
          <button type="button" className="btn btn-secondary btn-sm" onClick={loadFromRecordsUrl}>
            Import from query…
          </button>
          {canSchemaWrite && (
            <button type="button" className="btn btn-primary" disabled={saving} onClick={() => void saveView()}>
              {saving ? 'Saving…' : 'Save'}
            </button>
          )}
          {canSaveNav && (
            <button type="button" className="btn btn-secondary" disabled={saving} onClick={() => void saveToSidebar()}>
              Save to sidebar link
            </button>
          )}
        </div>
      </header>

      <div className="builder-name-row" style={{ display: 'flex', flexWrap: 'wrap', gap: 16, alignItems: 'flex-end' }}>
        <label className="field-label" style={{ flex: '1 1 220px', margin: 0 }}>
          View name
          <input
            className="input"
            value={viewName}
            onChange={(e) => setViewName(e.target.value)}
            readOnly={!canSchemaWrite}
            placeholder="e.g. Default table"
          />
        </label>
        <label className="field-label" style={{ flex: '1 1 200px', margin: 0 }}>
          Switch view
          <select
            className="input"
            value={isNew ? 'new' : viewId}
            onChange={(e) => onSwitchView(e.target.value)}
          >
            <option value="new">+ New list view</option>
            {views.map((v) => (
              <option key={v.id} value={v.id}>
                {v.name}
                {v.isDefault ? ' (default)' : ''}
              </option>
            ))}
          </select>
        </label>
        <label className="field-label row" style={{ margin: 0 }}>
          <input
            type="checkbox"
            checked={showRowActions}
            onChange={(e) => setShowRowActions(e.target.checked)}
            disabled={!canSchemaWrite}
          />
          <span>Show row actions</span>
        </label>
        <label className="field-label row" style={{ margin: 0 }} title="Used when Records has no ?view= in the URL">
          <input
            type="checkbox"
            checked={viewIsDefault}
            onChange={(e) => setViewIsDefault(e.target.checked)}
            disabled={!canSchemaWrite}
          />
          <span>Default list view for entity</span>
        </label>
      </div>

      {candidates.length > 1 && canCreatePortalNavItem && (
        <div className="builder-name-row" style={{ paddingTop: 0 }}>
          <label className="field-label" style={{ maxWidth: 420, margin: 0 }}>
            Sidebar link to update (Save to sidebar)
            <select className="input" value={navItemId} onChange={(e) => setNavItemId(e.target.value)}>
              <option value="">— Select —</option>
              {candidates.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.label}
                </option>
              ))}
            </select>
          </label>
        </div>
      )}

      {candidates.length === 0 && canCreatePortalNavItem && (
        <p className="builder-muted" style={{ margin: '0 1rem', fontSize: '0.875rem' }}>
          No internal nav item points at this entity&apos;s records list — you can still save views; use{' '}
          <Link to="/ui/create">Create UI</Link> to add a sidebar link.
        </p>
      )}

      {error && (
        <p role="alert" className="text-error" style={{ margin: '0 1rem' }}>
          {error}
        </p>
      )}

      {loadError && (
        <p role="alert" className="text-error" style={{ margin: '0 1rem' }}>
          {loadError}
        </p>
      )}

      <div className="builder-grid">
        <ListDesignerFieldsPanel
          fields={fields}
          columns={columns}
          search={fieldSearch}
          onSearchChange={setFieldSearch}
          filter={fieldFilter}
          onFilterChange={setFieldFilter}
          onAddColumn={addColumn}
          onOpenCreateField={() => setCreateFieldOpen(true)}
          onOpenEditField={(f) => setEditField(f)}
          schemaWritable={canSchemaWrite}
        />

        <section className="builder-panel builder-panel-grow">
          <div className="builder-panel-header">
            <h2>List structure</h2>
            <span className="builder-muted" style={{ fontSize: '0.75rem' }}>
              Table column order. Select a column to edit properties on the right. Use ↑ ↓ to reorder.
            </span>
          </div>
          <div className="builder-structure">
            <div className="builder-region">
              <div className="builder-region-head">
                <span className="builder-role">Columns</span>
              </div>
              {columns.length === 0 && <p className="builder-muted">Add fields from the data dictionary.</p>}
              <ul className="builder-items">
                {columns.map((c, idx) => (
                  <li key={`${c.fieldSlug}-${idx}`}>
                    <button
                      type="button"
                      className={`builder-item-btn${selectedIdx === idx ? ' builder-selected' : ''}`}
                      onClick={() => setSelectedIdx(idx)}
                    >
                      <span className="builder-item-title">
                        {fields.find((f) => f.slug === c.fieldSlug)?.name ?? c.fieldSlug}
                      </span>
                      <span className="builder-field-meta">
                        <code>{c.fieldSlug}</code>
                      </span>
                      <div className="builder-item-actions">
                        <button
                          type="button"
                          className="btn btn-xs btn-secondary"
                          disabled={!canSchemaWrite || idx === 0}
                          onClick={(e) => {
                            e.stopPropagation();
                            moveColumn(idx, -1);
                          }}
                        >
                          ↑
                        </button>
                        <button
                          type="button"
                          className="btn btn-xs btn-secondary"
                          disabled={!canSchemaWrite || idx >= columns.length - 1}
                          onClick={(e) => {
                            e.stopPropagation();
                            moveColumn(idx, 1);
                          }}
                        >
                          ↓
                        </button>
                        <button
                          type="button"
                          className="btn btn-xs btn-secondary"
                          disabled={!canSchemaWrite}
                          onClick={(e) => {
                            e.stopPropagation();
                            removeColumn(idx);
                          }}
                        >
                          Remove
                        </button>
                      </div>
                    </button>
                  </li>
                ))}
              </ul>
            </div>
          </div>
          <div style={{ marginTop: 12 }}>
            <h3 className="builder-muted" style={{ fontSize: '0.8125rem', margin: '0 0 8px' }}>
              Header preview
            </h3>
            <div style={{ overflowX: 'auto', border: '1px solid #e4e4e7', borderRadius: 8, background: '#fff' }}>
              <table className="records-table" style={{ margin: 0, minWidth: 200 }}>
                <thead>
                  <tr>
                    {columns.map((c) => (
                      <th key={c.fieldSlug}>
                        {c.label?.trim() || fields.find((f) => f.slug === c.fieldSlug)?.name || c.fieldSlug}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  <tr>
                    {columns.map((c) => (
                      <td key={c.fieldSlug} className="builder-muted">
                        …
                      </td>
                    ))}
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </section>

        <aside className="builder-panel">
          <div className="builder-panel-header">
            <h2>Column properties</h2>
          </div>
          {!selectedCol && <p className="builder-muted">Select a column in the list structure.</p>}
          {selectedCol && selectedIdx !== null && (
            <div style={{ display: 'grid', gap: 10 }}>
              <label className="field-label">
                Label override
                <input
                  className="input"
                  value={selectedCol.label ?? ''}
                  onChange={(e) => updateSelected({ label: e.target.value || undefined })}
                  placeholder={fields.find((f) => f.slug === selectedCol.fieldSlug)?.name ?? ''}
                  readOnly={!canSchemaWrite}
                />
              </label>
              <label className="field-label">
                Align
                <select
                  className="input"
                  value={selectedCol.align ?? ''}
                  onChange={(e) =>
                    updateSelected({
                      align: (e.target.value as 'left' | 'center' | 'right' | '') || undefined,
                    })
                  }
                  disabled={!canSchemaWrite}
                >
                  <option value="">Default</option>
                  <option value="left">Left</option>
                  <option value="center">Center</option>
                  <option value="right">Right</option>
                </select>
              </label>
              <label className="field-label">
                Width hint
                <select
                  className="input"
                  value={
                    typeof selectedCol.width === 'string'
                      ? selectedCol.width
                      : selectedCol.width === undefined
                        ? ''
                        : '__px__'
                  }
                  onChange={(e) => {
                    const v = e.target.value;
                    if (v === '' || v === 'narrow' || v === 'medium' || v === 'wide') {
                      updateSelected({ width: v || undefined });
                    }
                  }}
                  disabled={!canSchemaWrite}
                >
                  <option value="">Default</option>
                  <option value="narrow">Narrow</option>
                  <option value="medium">Medium</option>
                  <option value="wide">Wide</option>
                </select>
              </label>
              <label className="field-label row">
                <input
                  type="checkbox"
                  checked={Boolean(selectedCol.inlineEditable)}
                  onChange={(e) => updateSelected({ inlineEditable: e.target.checked })}
                  disabled={!canSchemaWrite}
                />
                <span>Inline editable</span>
              </label>
              <label className="field-label row">
                <input
                  type="checkbox"
                  checked={Boolean(selectedCol.linkToRecord)}
                  onChange={(e) => updateSelected({ linkToRecord: e.target.checked })}
                  disabled={!canSchemaWrite}
                />
                <span>Link to record detail</span>
              </label>
              <label className="field-label row">
                <input
                  type="checkbox"
                  checked={selectedCol.visible !== false}
                  onChange={(e) => updateSelected({ visible: e.target.checked })}
                  disabled={!canSchemaWrite}
                />
                <span>Visible</span>
              </label>
            </div>
          )}
        </aside>
      </div>

      <p className="builder-muted" style={{ margin: '8px 16px 16px', fontSize: '0.75rem', wordBreak: 'break-all' }}>
        <strong>routePath:</strong> <code>{recordsListPath(entityId, listState)}</code>
      </p>

      {createFieldOpen && (
        <CreateFieldModal
          entityId={entityId}
          onClose={() => setCreateFieldOpen(false)}
          onCreated={(f) => {
            setFields((prev) => [...prev, f].sort((a, b) => a.name.localeCompare(b.name)));
            setCreateFieldOpen(false);
          }}
        />
      )}
      {editField && (
        <EditFieldModal
          entityId={entityId}
          field={editField}
          onClose={() => setEditField(null)}
          onUpdated={(f) =>
            setFields((prev) =>
              prev
                .map((x) => (x.id === f.id ? f : x))
                .sort((a, b) => a.name.localeCompare(b.name))
            )
          }
        />
      )}
    </div>
  );
}

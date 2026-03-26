import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { Button, Checkbox, Code, Select, Table, Text, TextInput } from '@mantine/core';
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
import { canMutateEntityDefinition } from '../auth/jwtPermissions';
import { CreateFieldModal } from '../components/builder/CreateFieldModal';
import { EditFieldModal } from '../components/builder/EditFieldModal';
import { ListDesignerFieldsPanel } from '../components/builder/ListDesignerFieldsPanel';
import { clearPortalNavigationCache, usePortalNavigation } from '../hooks/usePortalNavigation';
import { publishDesignArtifacts } from '../ui/publishDesignArtifacts';
import {
  buildRecordListViewDefinitionV1,
  parseRecordListViewDefinition,
  RECORD_LIST_ROW_ID_SLUG,
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
  const { canSchemaWrite, canCreatePortalNavItem, permissions } = useAuth();

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
  /** When true, Records shows the leading UUID column for this view. */
  const [showRecordId, setShowRecordId] = useState(true);
  /** When true, this view is the entity default for /records without ?view=. */
  const [viewIsDefault, setViewIsDefault] = useState(false);

  const [createFieldOpen, setCreateFieldOpen] = useState(false);
  const [editField, setEditField] = useState<EntityFieldDto | null>(null);

  const [viewLifecycleStatus, setViewLifecycleStatus] = useState('ACTIVE');
  const [linkedNavItemId, setLinkedNavItemId] = useState('');
  const [linkedFormLayoutId, setLinkedFormLayoutId] = useState('');

  const canMutateFieldDefs = useMemo(
    () => (entity ? canMutateEntityDefinition(permissions, entity) : false),
    [entity, permissions]
  );

  useEffect(() => {
    const st = location.state as { linkedNavigationItemId?: string; linkedFormLayoutId?: string } | undefined;
    setLinkedNavItemId(st?.linkedNavigationItemId ?? '');
    setLinkedFormLayoutId(st?.linkedFormLayoutId ?? '');
  }, [location.key, location.state]);

  const listState = useMemo((): ListViewQueryState => {
    if (workingViewId) {
      // Sidebar URL uses showRecordId=0 only when Id is hidden; otherwise the saved definition applies.
      return {
        viewId: workingViewId,
        cols: [],
        inlineSlugs: [],
        showRowActions,
        showRecordId: showRecordId ? undefined : false,
      };
    }
    const ordered = normalizeOrders(columns);
    return {
      viewId: null,
      cols: ordered.map((c) => c.fieldSlug),
      inlineSlugs: ordered.filter((c) => c.inlineEditable).map((c) => c.fieldSlug),
      showRowActions,
    };
  }, [workingViewId, columns, showRowActions, showRecordId]);

  const recordsPreviewPath = useMemo(() => recordsListPath(entityId, listState), [entityId, listState]);

  /** Suppresses setState from stale in-flight loads (Strict Mode double mount, rapid route changes). */
  const loadGenerationRef = useRef(0);

  const load = useCallback(async () => {
    const eid = entityId.trim();
    const vid = viewId.trim();
    if (!eid || !vid) return;
    const gen = ++loadGenerationRef.current;
    setLoadError(null);
    try {
      const [e, f, vlist] = await Promise.all([
        getEntity(eid),
        listFields(eid),
        listRecordListViews(eid),
      ]);
      if (gen !== loadGenerationRef.current) return;
      setEntity(e);
      setFields([...f].sort((a, b) => a.name.localeCompare(b.name)));
      setViews(vlist);

      if (isNew) {
        setWorkingViewId(null);
        setViewName('');
        setColumns([]);
        setSelectedIdx(null);
        setShowRowActions(true);
        setShowRecordId(true);
        setViewIsDefault(false);
        setViewLifecycleStatus('ACTIVE');
        const fromQs = (location.state as { fromRecordsQuery?: string } | null)?.fromRecordsQuery;
        if (fromQs) {
          const parsed = parseListViewQueryFromRoutePath(`?${fromQs}`);
          setShowRowActions(parsed.showRowActions);
          setColumns(normalizeOrders(legacyColumnsFromQuery(parsed.cols, parsed.inlineSlugs)));
          if (parsed.viewId && canSchemaWrite) {
            try {
              const dto = await getRecordListView(eid, parsed.viewId);
              if (gen !== loadGenerationRef.current) return;
              setViewLifecycleStatus(dto.status);
              const def = parseRecordListViewDefinition(dto.definition);
              if (def) {
                setWorkingViewId(parsed.viewId);
                setViewName(dto.name);
                setColumns(normalizeOrders(def.columns));
                setShowRowActions(def.showRowActions !== false);
                setShowRecordId(
                  parsed.showRecordId !== undefined ? parsed.showRecordId : def.showRecordId !== false
                );
                setViewIsDefault(dto.isDefault);
                navigate(`/entities/${eid}/list-views/${parsed.viewId}`, { replace: true });
              }
            } catch {
              /* keep legacy cols from URL */
            }
          }
        }
      } else {
        const dto = await getRecordListView(eid, vid);
        if (gen !== loadGenerationRef.current) return;
        setViewLifecycleStatus(dto.status);
        const def = parseRecordListViewDefinition(dto.definition);
        setViewName(dto.name);
        setWorkingViewId(dto.id);
        if (!def) {
          setLoadError('This list view is not valid version 1 JSON. You can rebuild columns and save to fix the stored definition.');
          setColumns([]);
          setViewIsDefault(dto.isDefault);
        } else {
          setLoadError(null);
          setColumns(normalizeOrders(def.columns));
          setShowRowActions(def.showRowActions !== false);
          setShowRecordId(def.showRecordId !== false);
          setViewIsDefault(dto.isDefault);
        }
      }
    } catch (err) {
      if (gen !== loadGenerationRef.current) return;
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

  const switchViewSelectValue = useMemo(
    () =>
      isNew ? 'new' : views.some((v) => v.id === viewId) ? viewId : viewId || 'new',
    [isNew, views, viewId]
  );

  const switchViewSelectData = useMemo(() => {
    const rows: { value: string; label: string }[] = [{ value: 'new', label: '+ New list view' }];
    if (!isNew && viewId && !views.some((v) => v.id === viewId)) {
      rows.push({
        value: viewId,
        label: `${viewName || 'Current view'}${viewIsDefault ? ' (default)' : ''}`,
      });
    }
    for (const v of views) {
      rows.push({ value: v.id, label: `${v.name}${v.isDefault ? ' (default)' : ''}` });
    }
    return rows;
  }, [isNew, viewId, views, viewName, viewIsDefault]);

  function addColumn(slug: string) {
    if (!canSchemaWrite) return;
    if (slug.trim().toLowerCase() === RECORD_LIST_ROW_ID_SLUG) return;
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
      const def = buildRecordListViewDefinitionV1(normalizeOrders(columns), showRowActions, showRecordId);
      const body = {
        name,
        definition: def as unknown as Record<string, unknown>,
        isDefault: viewIsDefault,
      };
      let savedId = workingViewId;
      if (workingViewId) {
        const updated = await patchRecordListView(entityId, workingViewId, body);
        setViewLifecycleStatus(updated.status);
      } else {
        const created = await createRecordListView(entityId, body);
        setViewLifecycleStatus(created.status);
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

  const effectiveListViewId = workingViewId || (!isNew ? viewId : null);
  const showWipPublish =
    canSchemaWrite &&
    !!linkedNavItemId &&
    viewLifecycleStatus === 'WIP' &&
    !!effectiveListViewId;

  async function publishWip() {
    if (!linkedNavItemId || !effectiveListViewId) return;
    setSaving(true);
    setError(null);
    try {
      await publishDesignArtifacts({
        entityId,
        navigationItemId: linkedNavItemId,
        listViewId: effectiveListViewId,
        formLayoutId: linkedFormLayoutId || undefined,
      });
      const dto = await getRecordListView(entityId, effectiveListViewId);
      setViewLifecycleStatus(dto.status);
      void refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Publish failed');
    } finally {
      setSaving(false);
    }
  }

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
            setShowRecordId(
              parsed.showRecordId !== undefined ? parsed.showRecordId : def.showRecordId !== false
            );
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
      setShowRecordId(true);
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
          <Button component={Link} variant="default" size="sm" to={recordsPreviewPath}>
            Open records (preview)
          </Button>
          <Button type="button" variant="default" size="sm" onClick={loadFromRecordsUrl}>
            Import from query…
          </Button>
          {canSchemaWrite && (
            <Button type="button" disabled={saving} onClick={() => void saveView()}>
              {saving ? 'Saving…' : 'Save'}
            </Button>
          )}
          {canSaveNav && (
            <Button type="button" variant="default" disabled={saving} onClick={() => void saveToSidebar()}>
              Save to sidebar link
            </Button>
          )}
        </div>
      </header>

      {showWipPublish && (
        <div
          role="status"
          className="builder-muted"
          style={{
            margin: '0 1rem 12px',
            padding: '12px 14px',
            background: 'var(--builder-warn-bg, #fef3c7)',
            borderRadius: 8,
            fontSize: '0.9rem',
          }}
        >
          <strong>Work in progress</strong> — Sidebar link and related layouts are not published yet.{' '}
          {linkedFormLayoutId ? (
            <>
              Edit the{' '}
              <Link
                to={`/entities/${entityId}/layouts/${linkedFormLayoutId}`}
                state={{
                  linkedNavigationItemId: linkedNavItemId,
                  linkedListViewId: effectiveListViewId ?? undefined,
                }}
              >
                form layout
              </Link>{' '}
              if needed, then publish all together.
            </>
          ) : null}
          <Button type="button" size="sm" style={{ marginLeft: 12 }} disabled={saving} onClick={() => void publishWip()}>
            {saving ? 'Publishing…' : 'Publish'}
          </Button>
        </div>
      )}

      <div className="builder-name-row" style={{ display: 'flex', flexWrap: 'wrap', gap: 16, alignItems: 'flex-end' }}>
        <TextInput
          label="View name"
          style={{ flex: '1 1 220px' }}
          value={viewName}
          onChange={(e) => setViewName(e.target.value)}
          readOnly={!canSchemaWrite}
          placeholder="e.g. Default table"
        />
        <Select
          label="Switch view"
          style={{ flex: '1 1 200px' }}
          value={switchViewSelectValue}
          onChange={(v) => v && onSwitchView(v)}
          data={switchViewSelectData}
        />
        <Checkbox
          checked={showRowActions}
          onChange={(e) => setShowRowActions(e.currentTarget.checked)}
          disabled={!canSchemaWrite}
          label="Show row actions"
        />
        <Checkbox
          checked={showRecordId}
          onChange={(e) => setShowRecordId(e.currentTarget.checked)}
          disabled={!canSchemaWrite}
          label="Show record ID column"
        />
        <Checkbox
          checked={viewIsDefault}
          onChange={(e) => setViewIsDefault(e.currentTarget.checked)}
          disabled={!canSchemaWrite}
          label="Default list view for entity"
          title="Used when Records has no ?view= in the URL"
        />
      </div>

      {candidates.length > 1 && canCreatePortalNavItem && (
        <div className="builder-name-row" style={{ paddingTop: 0 }}>
          <Select
            label="Sidebar link to update (Save to sidebar)"
            placeholder="— Select —"
            maw={420}
            value={navItemId || null}
            onChange={(v) => setNavItemId(v ?? '')}
            data={candidates.map((c) => ({ value: c.id, label: c.label }))}
          />
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
          onOpenCreateField={() => {
            if (!canMutateFieldDefs) return;
            setCreateFieldOpen(true);
          }}
          onOpenEditField={(f) => setEditField(f)}
          schemaWritable={canSchemaWrite}
          fieldDefinitionsWritable={canMutateFieldDefs}
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
              <p className="builder-muted" style={{ fontSize: '0.75rem', margin: '0 0 8px' }}>
                Record UUID can be shown or hidden with <strong>Show record ID column</strong> above (not a data-dictionary
                field).
              </p>
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
                        <Button
                          type="button"
                          variant="default"
                          size="xs"
                          disabled={!canSchemaWrite || idx === 0}
                          onClick={(e) => {
                            e.stopPropagation();
                            moveColumn(idx, -1);
                          }}
                        >
                          ↑
                        </Button>
                        <Button
                          type="button"
                          variant="default"
                          size="xs"
                          disabled={!canSchemaWrite || idx >= columns.length - 1}
                          onClick={(e) => {
                            e.stopPropagation();
                            moveColumn(idx, 1);
                          }}
                        >
                          ↓
                        </Button>
                        <Button
                          type="button"
                          variant="default"
                          size="xs"
                          disabled={!canSchemaWrite}
                          onClick={(e) => {
                            e.stopPropagation();
                            removeColumn(idx);
                          }}
                        >
                          Remove
                        </Button>
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
            <Table.ScrollContainer minWidth={200} type="scroll" style={{ borderRadius: 8 }}>
              <Table withTableBorder withColumnBorders>
                <Table.Thead>
                  <Table.Tr>
                    {showRecordId && <Table.Th>Id</Table.Th>}
                    {columns.map((c) => (
                      <Table.Th key={c.fieldSlug}>
                        {c.label?.trim() || fields.find((f) => f.slug === c.fieldSlug)?.name || c.fieldSlug}
                      </Table.Th>
                    ))}
                  </Table.Tr>
                </Table.Thead>
                <Table.Tbody>
                  <Table.Tr>
                    {showRecordId && (
                      <Table.Td>
                        <Code c="dimmed">…</Code>
                      </Table.Td>
                    )}
                    {columns.map((c) => (
                      <Table.Td key={c.fieldSlug}>
                        <Text span c="dimmed">
                          …
                        </Text>
                      </Table.Td>
                    ))}
                  </Table.Tr>
                </Table.Tbody>
              </Table>
            </Table.ScrollContainer>
          </div>
        </section>

        <aside className="builder-panel">
          <div className="builder-panel-header">
            <h2>Column properties</h2>
          </div>
          {!selectedCol && <p className="builder-muted">Select a column in the list structure.</p>}
          {selectedCol && selectedIdx !== null && (
            <div style={{ display: 'grid', gap: 10 }}>
              <TextInput
                label="Label override"
                value={selectedCol.label ?? ''}
                onChange={(e) => updateSelected({ label: e.target.value || undefined })}
                placeholder={fields.find((f) => f.slug === selectedCol.fieldSlug)?.name ?? ''}
                readOnly={!canSchemaWrite}
              />
              <Select
                label="Align"
                value={selectedCol.align ?? ''}
                onChange={(v) =>
                  updateSelected({
                    align: (v as 'left' | 'center' | 'right' | '') || undefined,
                  })
                }
                disabled={!canSchemaWrite}
                data={[
                  { value: '', label: 'Default' },
                  { value: 'left', label: 'Left' },
                  { value: 'center', label: 'Center' },
                  { value: 'right', label: 'Right' },
                ]}
              />
              <Select
                label="Width hint"
                value={
                  typeof selectedCol.width === 'string'
                    ? selectedCol.width
                    : selectedCol.width === undefined
                      ? ''
                      : '__px__'
                }
                onChange={(v) => {
                  if (v === '' || v === 'narrow' || v === 'medium' || v === 'wide') {
                    updateSelected({ width: v || undefined });
                  }
                }}
                disabled={!canSchemaWrite}
                data={[
                  { value: '', label: 'Default' },
                  { value: 'narrow', label: 'Narrow' },
                  { value: 'medium', label: 'Medium' },
                  { value: 'wide', label: 'Wide' },
                ]}
              />
              <Checkbox
                checked={Boolean(selectedCol.inlineEditable)}
                onChange={(e) => updateSelected({ inlineEditable: e.currentTarget.checked })}
                disabled={!canSchemaWrite}
                label="Inline editable"
              />
              <Checkbox
                checked={Boolean(selectedCol.linkToRecord)}
                onChange={(e) => updateSelected({ linkToRecord: e.currentTarget.checked })}
                disabled={!canSchemaWrite}
                label="Link to record detail"
              />
              <Checkbox
                checked={selectedCol.visible !== false}
                onChange={(e) => updateSelected({ visible: e.currentTarget.checked })}
                disabled={!canSchemaWrite}
                label="Visible"
              />
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

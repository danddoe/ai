import { useCallback, useEffect, useMemo, useRef, useState, type CSSProperties } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import {
  getEntity,
  getRecordListView,
  listFields,
  listRecordListViews,
  listRecords,
  queryRecords,
  deleteRecord,
  patchRecord,
  type EntityDto,
  type EntityFieldDto,
  type RecordDto,
  type RecordListViewDto,
} from '../api/schemas';
import {
  type RecordListColumnDefinition,
  parseRecordListViewDefinition,
} from '../utils/recordListViewDefinition';
import { useAuth } from '../auth/AuthProvider';
import { buildRecordSearchFilter } from '../utils/recordListSearch';

function shortId(id: string): string {
  return id.length > 10 ? `${id.slice(0, 8)}…` : id;
}

function displayForRecord(entity: EntityDto, row: RecordDto): string {
  const slug = entity.defaultDisplayFieldSlug?.trim();
  if (slug) {
    const v = row.values[slug];
    if (v !== undefined && v !== null && String(v) !== '') return String(v);
  }
  if (row.externalId) return row.externalId;
  const keys = Object.keys(row.values);
  if (keys.length > 0) {
    const v = row.values[keys[0]];
    if (v !== undefined && v !== null) return String(v);
  }
  return '—';
}

function parseCommaSlugs(raw: string | null): string[] {
  if (!raw || !raw.trim()) return [];
  return raw
    .split(',')
    .map((s) => s.trim())
    .filter((s) => s.length > 0);
}

function formatCellValue(row: RecordDto, slug: string, field?: EntityFieldDto): string {
  const v = row.values[slug];
  if (v === undefined || v === null) return '—';
  const ft = field?.fieldType?.toLowerCase() ?? '';
  if (ft === 'boolean') return v === true || v === 'true' ? 'Yes' : v === false || v === 'false' ? 'No' : String(v);
  if (typeof v === 'object') return JSON.stringify(v);
  return String(v);
}

const SEARCH_DEBOUNCE_MS = 400;

const VIEW_UUID_RE =
  /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/;

function columnWidthStyle(width: string | number | undefined): CSSProperties | undefined {
  if (width === undefined || width === null) return undefined;
  if (typeof width === 'number') return { width: width, maxWidth: width };
  if (width === 'narrow') return { width: 100, maxWidth: 120 };
  if (width === 'medium') return { width: 160, maxWidth: 200 };
  if (width === 'wide') return { width: 240, maxWidth: 320 };
  return undefined;
}

export function EntityRecordsListPage() {
  const { entityId = '' } = useParams();
  const [searchParams, setSearchParams] = useSearchParams();
  const { tenantId, canRecordsRead, canRecordsWrite, canCreatePortalNavItem, canSchemaWrite } = useAuth();

  const page = Math.max(1, parseInt(searchParams.get('page') || '1', 10) || 1);
  const pageSize = Math.min(200, Math.max(1, parseInt(searchParams.get('pageSize') || '50', 10) || 50));
  const qParam = (searchParams.get('q') ?? '').slice(0, 200);
  const viewIdParam = useMemo(() => {
    const v = (searchParams.get('view') ?? '').trim();
    return VIEW_UUID_RE.test(v) ? v : '';
  }, [searchParams]);
  const colsRaw = searchParams.get('cols') ?? '';
  const colsSlugs = useMemo(() => parseCommaSlugs(colsRaw), [colsRaw]);
  const inlineRaw = searchParams.get('inline') ?? '';
  const legacyCustomColumns = !viewIdParam && colsSlugs.length > 0;

  const [entity, setEntity] = useState<EntityDto | null>(null);
  const [fields, setFields] = useState<EntityFieldDto[] | null>(null);
  /**
   * Column config from entity-builder when URL has <code>?view=</code>, or when the entity has a default list view
   * (legacy <code>cols</code> in the URL still wins and skips default resolution).
   */
  const [savedListView, setSavedListView] = useState<{
    columns: RecordListColumnDefinition[];
    showRowActions: boolean;
  } | null>(null);
  /** Set when columns come from the default list view without <code>?view=</code> — used for “Edit list view”. */
  const [autoResolvedViewId, setAutoResolvedViewId] = useState<string | null>(null);
  /** Shown in the header; set whenever the grid uses a saved list view definition. */
  const [activeListViewInfo, setActiveListViewInfo] = useState<{
    id: string;
    name: string;
    isDefault: boolean;
  } | null>(null);
  /** For the list-view switcher (empty when <code>cols=</code> legacy mode). */
  const [listViewsBrief, setListViewsBrief] = useState<RecordListViewDto[]>([]);
  const [items, setItems] = useState<RecordDto[]>([]);
  const [total, setTotal] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const [searchDraft, setSearchDraft] = useState(qParam);
  const searchDebounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  useEffect(() => {
    setSearchDraft(qParam);
  }, [qParam]);

  useEffect(() => {
    if (searchDebounceRef.current) clearTimeout(searchDebounceRef.current);
    searchDebounceRef.current = setTimeout(() => {
      const next = searchDraft.slice(0, 200);
      if (next === qParam) return;
      setSearchParams(
        (prev) => {
          const n = new URLSearchParams(prev);
          if (!next.trim()) n.delete('q');
          else n.set('q', next.trim());
          n.set('page', '1');
          return n;
        },
        { replace: true }
      );
    }, SEARCH_DEBOUNCE_MS);
    return () => {
      if (searchDebounceRef.current) clearTimeout(searchDebounceRef.current);
    };
  }, [searchDraft, qParam, setSearchParams]);

  const fieldBySlug = useMemo(() => {
    const m = new Map<string, EntityFieldDto>();
    for (const f of fields ?? []) m.set(f.slug, f);
    return m;
  }, [fields]);

  const visibleColSlugs = useMemo(() => {
    if (savedListView) {
      return savedListView.columns.map((c) => c.fieldSlug).filter((s) => fieldBySlug.has(s));
    }
    if (!legacyCustomColumns) return [];
    return colsSlugs.filter((s) => fieldBySlug.has(s));
  }, [savedListView, legacyCustomColumns, colsSlugs, fieldBySlug]);

  const colMetaBySlug = useMemo(() => {
    const m = new Map<string, RecordListColumnDefinition>();
    if (savedListView) {
      for (const c of savedListView.columns) m.set(c.fieldSlug, c);
    }
    return m;
  }, [savedListView]);

  const inlineSlugs = useMemo(() => {
    if (savedListView) {
      return new Set(savedListView.columns.filter((c) => c.inlineEditable).map((c) => c.fieldSlug));
    }
    return new Set(parseCommaSlugs(inlineRaw));
  }, [savedListView, inlineRaw]);

  const showActions = savedListView ? savedListView.showRowActions : searchParams.get('actions') !== '0';

  const useCustomLayout = visibleColSlugs.length > 0 && (savedListView !== null || legacyCustomColumns);

  const load = useCallback(async () => {
    if (!tenantId || !entityId || !canRecordsRead) return;
    setError(null);
    setBusy(true);
    try {
      const useSavedViews = colsSlugs.length === 0;
      const [e, flds, viewsForPicker] = await Promise.all([
        getEntity(entityId),
        listFields(entityId),
        useSavedViews ? listRecordListViews(entityId) : Promise.resolve([] as RecordListViewDto[]),
      ]);
      setEntity(e);
      setFields(flds);
      setListViewsBrief(viewsForPicker);

      const bySlug = new Map(flds.map((f) => [f.slug, f]));

      let viewCols: RecordListColumnDefinition[] = [];
      let appliedSavedListView = false;

      if (viewIdParam) {
        setAutoResolvedViewId(null);
        const dto = await getRecordListView(entityId, viewIdParam);
        const def = parseRecordListViewDefinition(dto.definition);
        if (!def) {
          throw new Error('List view definition is missing or not version 1');
        }
        viewCols = def.columns.filter((c) => c.visible !== false);
        setSavedListView({ columns: viewCols, showRowActions: def.showRowActions !== false });
        setActiveListViewInfo({ id: dto.id, name: dto.name, isDefault: dto.isDefault });
        appliedSavedListView = true;
      } else if (colsSlugs.length > 0) {
        setSavedListView(null);
        setAutoResolvedViewId(null);
        setActiveListViewInfo(null);
      } else {
        const defMeta = viewsForPicker.find((v) => v.isDefault);
        if (!defMeta) {
          setSavedListView(null);
          setAutoResolvedViewId(null);
          setActiveListViewInfo(null);
        } else {
          const dto = await getRecordListView(entityId, defMeta.id);
          const def = parseRecordListViewDefinition(dto.definition);
          if (!def) {
            setSavedListView(null);
            setAutoResolvedViewId(null);
            setActiveListViewInfo(null);
          } else {
            viewCols = def.columns.filter((c) => c.visible !== false);
            setSavedListView({ columns: viewCols, showRowActions: def.showRowActions !== false });
            setAutoResolvedViewId(defMeta.id);
            setActiveListViewInfo({ id: dto.id, name: dto.name, isDefault: dto.isDefault });
            appliedSavedListView = true;
          }
        }
      }

      let restrictSearch: string[] | null = null;
      if (appliedSavedListView) {
        const vis = viewCols.map((c) => c.fieldSlug).filter((s) => bySlug.has(s));
        restrictSearch = vis.length > 0 ? vis : null;
      } else {
        const parsedCols = parseCommaSlugs(colsRaw);
        const visible = parsedCols.filter((s) => bySlug.has(s));
        restrictSearch = parsedCols.length > 0 && visible.length > 0 ? visible : null;
      }
      const filter = buildRecordSearchFilter(flds, qParam, restrictSearch);

      if (filter) {
        const pr = await queryRecords(tenantId, entityId, { filter, page, pageSize });
        setItems(pr.items);
        setTotal(pr.total);
      } else {
        const pr = await listRecords(tenantId, entityId, { page, pageSize });
        setItems(pr.items);
        setTotal(pr.total);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load');
      setEntity(null);
      setFields(null);
      setItems([]);
      setTotal(0);
      setSavedListView(null);
      setAutoResolvedViewId(null);
      setActiveListViewInfo(null);
      setListViewsBrief([]);
    } finally {
      setBusy(false);
    }
  }, [tenantId, entityId, canRecordsRead, page, pageSize, qParam, colsRaw, colsSlugs.length, viewIdParam]);

  useEffect(() => {
    void load();
  }, [load]);

  function setPageParams(nextPage: number) {
    setSearchParams(
      (prev) => {
        const n = new URLSearchParams(prev);
        n.set('page', String(Math.max(1, nextPage)));
        return n;
      },
      { replace: true }
    );
  }

  function setPageSizeParams(nextSize: number) {
    const sz = Math.min(200, Math.max(1, nextSize));
    setSearchParams(
      (prev) => {
        const n = new URLSearchParams(prev);
        n.set('pageSize', String(sz));
        n.set('page', '1');
        return n;
      },
      { replace: true }
    );
  }

  async function onDelete(row: RecordDto) {
    if (!tenantId || !canRecordsWrite) return;
    if (!window.confirm(`Delete record ${shortId(row.id)}?`)) return;
    try {
      await deleteRecord(tenantId, entityId, row.id);
      await load();
    } catch (e) {
      window.alert(e instanceof Error ? e.message : 'Delete failed');
    }
  }

  async function saveInline(row: RecordDto, slug: string, raw: unknown) {
    if (!tenantId || !canRecordsWrite) return;
    const field = fieldBySlug.get(slug);
    const ft = field?.fieldType?.toLowerCase() ?? 'string';
    let value: unknown = raw;
    if (ft === 'number') {
      if (raw === '' || raw === null || raw === undefined) value = null;
      else value = Number(raw);
    } else if (ft === 'boolean') value = Boolean(raw);
    else value = raw === '' ? null : raw;
    try {
      const updated = await patchRecord(tenantId, entityId, row.id, { values: { [slug]: value } });
      setItems((prev) => prev.map((r) => (r.id === row.id ? updated : r)));
    } catch (e) {
      window.alert(e instanceof Error ? e.message : 'Save failed');
      await load();
    }
  }

  if (!tenantId) {
    return (
      <div className="page-shell">
        <p role="alert" className="text-error">
          Missing <code>tenant_id</code> in access token — cannot load tenant-scoped records.
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

  const totalPages = Math.max(1, Math.ceil(total / pageSize));

  return (
    <div className="page-shell page-shell-wide">
      <nav className="breadcrumb">
        <Link to="/entities">Entities</Link>
        <span aria-hidden> / </span>
        <Link to={`/entities/${entityId}/layouts`}>{entity?.name ?? '…'}</Link>
        <span aria-hidden> / </span>
        <span>Records</span>
      </nav>
      <header className="page-header">
        <div>
          <h1 className="page-title">Records</h1>
          <p className="page-sub">{entity ? `${entity.name} — browse and open records.` : 'Loading…'}</p>
          {entity && (
            <p className="builder-muted" style={{ fontSize: '0.875rem', marginTop: 8, maxWidth: 720 }}>
              {legacyCustomColumns && (
                <>
                  Active list: <strong>Custom columns</strong> from this page&apos;s link (<code>cols=</code>). Switching
                  saved views is disabled until that parameter is removed.
                </>
              )}
              {!legacyCustomColumns && activeListViewInfo && (
                <>
                  Active list view: <strong>{activeListViewInfo.name}</strong>
                  {activeListViewInfo.isDefault && (
                    <span className="pill pill-on" style={{ marginLeft: 8, verticalAlign: 'middle' }}>
                      default
                    </span>
                  )}
                  <span style={{ marginLeft: 8 }}>
                    {viewIdParam
                      ? '— URL includes an explicit list view id.'
                      : '— loaded as the entity default (URL has no list view id).'}
                  </span>
                </>
              )}
              {!legacyCustomColumns && !activeListViewInfo && (
                <>
                  Active list: <strong>Basic table</strong> (Id, display field, updated). Create list views under Form
                  layouts; the one marked <span className="pill pill-on">default</span> loads here automatically.
                </>
              )}
            </p>
          )}
        </div>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          {canRecordsWrite && (
            <Link className="btn btn-primary" to={`/entities/${entityId}/records/new`}>
              Add record
            </Link>
          )}
          {(canCreatePortalNavItem || canSchemaWrite) && (
            <Link
              className="btn btn-secondary"
              to={
                viewIdParam || autoResolvedViewId
                  ? `/entities/${entityId}/list-views/${viewIdParam || autoResolvedViewId}`
                  : `/entities/${entityId}/list-views/new`
              }
              state={{ fromRecordsQuery: searchParams.toString() }}
            >
              Edit list view
            </Link>
          )}
          <Link className="btn btn-secondary" to={`/entities/${entityId}/layouts#record-list-views`}>
            Form layouts
          </Link>
        </div>
      </header>

      <div style={{ marginBottom: 16, display: 'flex', flexWrap: 'wrap', gap: 12, alignItems: 'center' }}>
        <label className="field-label" style={{ margin: 0, flex: '1 1 240px', minWidth: 200 }}>
          <span className="builder-muted" style={{ fontSize: '0.8125rem' }}>
            Search
          </span>
          <input
            className="input"
            placeholder="Filter rows (text fields)…"
            value={searchDraft}
            onChange={(e) => setSearchDraft(e.target.value.slice(0, 200))}
            aria-label="Search records"
          />
        </label>
        <label className="field-label" style={{ margin: 0 }}>
          <span className="builder-muted" style={{ fontSize: '0.8125rem' }}>
            Page size
          </span>
          <select
            className="input"
            value={String(pageSize)}
            onChange={(e) => setPageSizeParams(parseInt(e.target.value, 10))}
            aria-label="Page size"
          >
            {[25, 50, 100, 200].map((n) => (
              <option key={n} value={n}>
                {n}
              </option>
            ))}
          </select>
        </label>
        {!legacyCustomColumns && listViewsBrief.length > 0 && (
          <label className="field-label" style={{ margin: 0, minWidth: 200 }}>
            <span className="builder-muted" style={{ fontSize: '0.8125rem' }}>
              Switch list view
            </span>
            <select
              className="input"
              aria-label="Switch list view"
              value={viewIdParam || ''}
              onChange={(e) => {
                const next = e.target.value;
                setSearchParams(
                  (prev) => {
                    const n = new URLSearchParams(prev);
                    if (!next) n.delete('view');
                    else n.set('view', next);
                    n.set('page', '1');
                    return n;
                  },
                  { replace: true }
                );
              }}
            >
              <option value="">
                {listViewsBrief.some((v) => v.isDefault)
                  ? `Default — ${listViewsBrief.find((v) => v.isDefault)?.name ?? '…'}`
                  : 'Basic table'}
              </option>
              {listViewsBrief.map((v) => (
                <option key={v.id} value={v.id}>
                  {v.name}
                  {v.isDefault ? ' ★' : ''}
                </option>
              ))}
            </select>
          </label>
        )}
      </div>

      {error && (
        <p role="alert" className="text-error">
          {error}
        </p>
      )}
      {busy && <p className="builder-muted">Loading…</p>}
      <div className="records-table-wrap">
        <table className="records-table">
          <thead>
            <tr>
              <th>Id</th>
              {useCustomLayout
                ? visibleColSlugs.map((slug) => {
                    const meta = colMetaBySlug.get(slug);
                    const label = meta?.label?.trim() || fieldBySlug.get(slug)?.name || slug;
                    const w = columnWidthStyle(meta?.width);
                    const ta = meta?.align;
                    const thStyle: CSSProperties = { ...w, ...(ta ? { textAlign: ta } : {}) };
                    return (
                      <th key={slug} style={thStyle}>
                        {label}
                      </th>
                    );
                  })
                : (
                    <>
                      <th>Display</th>
                      <th>Updated</th>
                    </>
                  )}
              {useCustomLayout && <th>Updated</th>}
              {showActions && <th />}
            </tr>
          </thead>
          <tbody>
            {items.map((row) => (
              <tr key={row.id}>
                <td>
                  <code>{shortId(row.id)}</code>
                </td>
                {useCustomLayout
                  ? visibleColSlugs.map((slug) => {
                      const f = fieldBySlug.get(slug);
                      const meta = colMetaBySlug.get(slug);
                      const tdStyle: CSSProperties = {
                        ...columnWidthStyle(meta?.width),
                        ...(meta?.align ? { textAlign: meta.align } : {}),
                      };
                      const inline = inlineSlugs.has(slug) && canRecordsWrite && f;
                      if (inline && f) {
                        const ft = f.fieldType.toLowerCase();
                        const v = row.values[slug];
                        const inputKey = `${row.id}-${slug}-${row.updatedAt ?? ''}`;
                        if (ft === 'boolean') {
                          return (
                            <td key={slug} style={tdStyle}>
                              <input
                                type="checkbox"
                                checked={v === true || v === 'true'}
                                onChange={(e) => void saveInline(row, slug, e.target.checked)}
                              />
                            </td>
                          );
                        }
                        if (ft === 'number') {
                          return (
                            <td key={slug} style={tdStyle}>
                              <input
                                key={inputKey}
                                type="number"
                                className="input"
                                style={{ maxWidth: 140, padding: '4px 8px', fontSize: '0.875rem' }}
                                defaultValue={v === null || v === undefined ? '' : String(v)}
                                onBlur={(e) => void saveInline(row, slug, e.target.value === '' ? null : e.target.value)}
                              />
                            </td>
                          );
                        }
                        return (
                          <td key={slug} style={tdStyle}>
                            <input
                              key={inputKey}
                              type="text"
                              className="input"
                              style={{ padding: '4px 8px', fontSize: '0.875rem' }}
                              defaultValue={v === null || v === undefined ? '' : String(v)}
                              onBlur={(e) => void saveInline(row, slug, e.target.value)}
                            />
                          </td>
                        );
                      }
                      const text = formatCellValue(row, slug, f);
                      const linkRec = meta?.linkToRecord;
                      return (
                        <td key={slug} style={tdStyle}>
                          {linkRec ? (
                            <Link className="link-btn" to={`/entities/${entityId}/records/${row.id}`}>
                              {text}
                            </Link>
                          ) : (
                            text
                          )}
                        </td>
                      );
                    })
                  : (
                      <>
                        <td>{entity ? displayForRecord(entity, row) : '—'}</td>
                        <td>{row.updatedAt ? new Date(row.updatedAt).toLocaleString() : '—'}</td>
                      </>
                    )}
                {useCustomLayout && <td>{row.updatedAt ? new Date(row.updatedAt).toLocaleString() : '—'}</td>}
                {showActions && (
                  <td>
                    <Link className="link-btn" to={`/entities/${entityId}/records/${row.id}`}>
                      Edit
                    </Link>
                    {canRecordsWrite && (
                      <>
                        {' · '}
                        <button type="button" className="link-btn" onClick={() => void onDelete(row)}>
                          Delete
                        </button>
                      </>
                    )}
                  </td>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {items.length === 0 && !busy && !error && (
        <p className="builder-muted">No records yet.{canRecordsWrite ? ' Use Add record to create one.' : ''}</p>
      )}
      {totalPages > 1 && (
        <div className="records-pager">
          <button
            type="button"
            className="btn btn-secondary btn-sm"
            disabled={page <= 1 || busy}
            onClick={() => setPageParams(page - 1)}
          >
            Previous
          </button>
          <span className="builder-muted">
            Page {page} of {totalPages} ({total} total)
          </span>
          <button
            type="button"
            className="btn btn-secondary btn-sm"
            disabled={page >= totalPages || busy}
            onClick={() => setPageParams(page + 1)}
          >
            Next
          </button>
        </div>
      )}
    </div>
  );
}

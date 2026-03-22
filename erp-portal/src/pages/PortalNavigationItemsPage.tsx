import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  createNavigationItem,
  listEntities,
  listNavigationAdminItems,
  patchNavigationItem,
  type EntityDto,
  type NavigationAdminItemDto,
  type NavigationItemPatchRequest,
} from '../api/schemas';
import { useAuth } from '../auth/AuthProvider';
import { Modal } from '../components/Modal';
import { clearPortalNavigationCache } from '../hooks/usePortalNavigation';
import { formatCategoryKeyLabel, PORTAL_NAV_CATEGORY_KEYS } from '../utils/portalNavCategoryKeys';
import { PORTAL_NAV_ICON_OPTIONS, PortalNavIcon } from '../ui/portalNavIcons';

const SEARCH_DEBOUNCE_MS = 380;
const PARENT_KEEP = '__keep__';

/** Path-only: entity records list or new-record screen (IAM also allows whitelisted query keys). */
const ENTITY_RECORDS_ROUTE_RE =
  /^\/entities\/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\/records(\/new)?$/;

/** Path-only: per-entity audit timeline hub. */
const ENTITY_AUDIT_ROUTE_RE =
  /^\/entities\/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\/audit$/;

/** Mirrors IAM {@code isAllowedInternalSpaRoute} for client-side validation. */
function isAllowedInternalSpaRouteClient(route: string): boolean {
  const trimmed = route.trim();
  if (!trimmed) return false;
  const qIdx = trimmed.indexOf('?');
  const pathRaw = (qIdx < 0 ? trimmed : trimmed.slice(0, qIdx)).trim();
  const pathOnly = pathRaw.length > 1 && pathRaw.endsWith('/') ? pathRaw.slice(0, -1) : pathRaw;
  const query = qIdx < 0 ? '' : trimmed.slice(qIdx + 1);
  if (query.trim() !== '' && (pathOnly === '/home' || pathOnly === '/entities' || pathOnly === '/audit')) {
    return false;
  }
  if (pathOnly === '/home' || pathOnly === '/entities' || pathOnly === '/audit') return true;
  if (ENTITY_RECORDS_ROUTE_RE.test(pathOnly)) return true;
  return ENTITY_AUDIT_ROUTE_RE.test(pathOnly);
}

/** IAM {@code portal_navigation_items.type}; labels are for the form only. */
const NAV_ITEM_TYPES: { value: string; label: string }[] = [
  { value: 'internal', label: 'Internal (SPA route)' },
  { value: 'external', label: 'External (URL)' },
  { value: 'section', label: 'Section (heading)' },
  { value: 'divider', label: 'Divider' },
];

function shortId(id: string): string {
  return id.length > 10 ? `${id.slice(0, 8)}…` : id;
}

function parseTokenTextarea(s: string): string[] {
  return s
    .split(/[\n,]+/)
    .map((t) => t.trim())
    .filter((t) => t.length > 0);
}

function matchesSearch(row: NavigationAdminItemDto, q: string): boolean {
  const ql = q.trim().toLowerCase();
  if (!ql) return true;
  if (row.label.toLowerCase().includes(ql)) return true;
  if ((row.description ?? '').toLowerCase().includes(ql)) return true;
  if ((row.routePath ?? '').toLowerCase().includes(ql)) return true;
  if ((row.categoryKey ?? '').toLowerCase().includes(ql)) return true;
  if (row.searchKeywords.some((k) => k.toLowerCase().includes(ql))) return true;
  return false;
}

type ModalMode = 'closed' | 'create' | 'edit';

export function PortalNavigationItemsPage() {
  const { canCreatePortalNavItem, canManageGlobalNavigation } = useAuth();
  const allowed = canCreatePortalNavItem || canManageGlobalNavigation;

  const [items, setItems] = useState<NavigationAdminItemDto[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [searchDraft, setSearchDraft] = useState('');
  const [searchQ, setSearchQ] = useState('');
  const searchDebounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const [modalMode, setModalMode] = useState<ModalMode>('closed');
  const [editing, setEditing] = useState<NavigationAdminItemDto | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const [createScope, setCreateScope] = useState<'TENANT' | 'GLOBAL'>('TENANT');
  const [entitiesPickList, setEntitiesPickList] = useState<EntityDto[] | null>(null);
  const [entityPickerId, setEntityPickerId] = useState('');
  const [entityRouteTarget, setEntityRouteTarget] = useState<'list' | 'new'>('list');

  const [parentChoice, setParentChoice] = useState(PARENT_KEEP);
  const [sortOrder, setSortOrder] = useState('0');
  const [routePath, setRoutePath] = useState('');
  const [label, setLabel] = useState('');
  const [description, setDescription] = useState('');
  const [icon, setIcon] = useState('');
  const [categoryKey, setCategoryKey] = useState('');
  const [searchKeywordsText, setSearchKeywordsText] = useState('');
  const [requiredPermissionsText, setRequiredPermissionsText] = useState('');
  const [requiredRolesText, setRequiredRolesText] = useState('');
  const [active, setActive] = useState(true);
  const [promoteToGlobal, setPromoteToGlobal] = useState(false);
  const [navType, setNavType] = useState('internal');

  const load = useCallback(async () => {
    setLoadError(null);
    try {
      const res = await listNavigationAdminItems();
      setItems(res.items);
    } catch (e) {
      setLoadError(e instanceof Error ? e.message : 'Failed to load navigation items');
      setItems(null);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (searchDebounceRef.current) clearTimeout(searchDebounceRef.current);
    searchDebounceRef.current = setTimeout(() => {
      setSearchQ(searchDraft.slice(0, 200));
    }, SEARCH_DEBOUNCE_MS);
    return () => {
      if (searchDebounceRef.current) clearTimeout(searchDebounceRef.current);
    };
  }, [searchDraft]);

  useEffect(() => {
    if (modalMode !== 'create') return;
    let cancelled = false;
    void (async () => {
      try {
        const list = await listEntities();
        if (!cancelled) setEntitiesPickList(list);
      } catch {
        if (!cancelled) setEntitiesPickList([]);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [modalMode]);

  const filtered = useMemo(() => {
    if (!items) return [];
    return items.filter((row) => matchesSearch(row, searchQ));
  }, [items, searchQ]);

  const parentOptions = useMemo(() => {
    if (!items) return [];
    const base = items.slice().sort((a, b) => a.label.localeCompare(b.label));
    if (modalMode === 'edit' && editing) {
      return base.filter((i) => i.id !== editing.id);
    }
    return base;
  }, [items, modalMode, editing]);

  const categoryKeySelectOptions = useMemo(() => {
    const set = new Set<string>(PORTAL_NAV_CATEGORY_KEYS);
    if (modalMode === 'edit' && editing?.categoryKey?.trim()) {
      set.add(editing.categoryKey.trim());
    }
    return [...set].sort((a, b) => a.localeCompare(b));
  }, [modalMode, editing?.categoryKey]);

  const iconIsLegacy = useMemo(() => {
    const k = icon.trim();
    if (!k) return false;
    return !PORTAL_NAV_ICON_OPTIONS.some((o) => o.key === k);
  }, [icon]);

  const applyEntityRoute = useCallback((entityId: string, target: 'list' | 'new') => {
    if (!entityId) return;
    setRoutePath(target === 'new' ? `/entities/${entityId}/records/new` : `/entities/${entityId}/records`);
  }, []);

  const openCreate = () => {
    setModalMode('create');
    setEditing(null);
    setFormError(null);
    setCreateScope('TENANT');
    setParentChoice('');
    setSortOrder('0');
    setRoutePath('');
    setLabel('');
    setDescription('');
    setIcon('');
    setCategoryKey('');
    setSearchKeywordsText('');
    setRequiredPermissionsText('');
    setRequiredRolesText('');
    setEntityPickerId('');
    setEntityRouteTarget('list');
    setEntitiesPickList(null);
    setNavType('internal');
  };

  const openEdit = (row: NavigationAdminItemDto) => {
    setModalMode('edit');
    setEditing(row);
    setFormError(null);
    setParentChoice(PARENT_KEEP);
    setSortOrder(String(row.sortOrder));
    setRoutePath(row.routePath ?? '');
    setLabel(row.label);
    setDescription(row.description ?? '');
    setIcon(row.icon ?? '');
    setCategoryKey(row.categoryKey ?? '');
    setSearchKeywordsText(row.searchKeywords.join('\n'));
    setRequiredPermissionsText(row.requiredPermissions.join('\n'));
    setRequiredRolesText(row.requiredRoles.join('\n'));
    setActive(row.active);
    setPromoteToGlobal(false);
    setNavType(row.type || 'internal');
  };

  const closeModal = () => {
    setModalMode('closed');
    setEditing(null);
    setFormError(null);
  };

  const submitCreate = async () => {
    setFormError(null);
    const sort = parseInt(sortOrder, 10);
    if (Number.isNaN(sort)) {
      setFormError('Sort order must be a number.');
      return;
    }
    if (!label.trim()) {
      setFormError('Label is required.');
      return;
    }
    const route = routePath.trim();
    if (navType === 'internal') {
      if (!isAllowedInternalSpaRouteClient(route)) {
        setFormError(
          'Internal type: use /home, /entities, /audit, /entities/{uuid}/audit, or /entities/{uuid}/records (or …/records/new; records may use allowed query params).'
        );
        return;
      }
    } else if (navType === 'external') {
      if (!route) {
        setFormError('External type requires a URL in route path.');
        return;
      }
    }
    if (createScope === 'GLOBAL' && !canManageGlobalNavigation) {
      setFormError('Global scope requires platform navigation admin.');
      return;
    }

    setSaving(true);
    try {
      await createNavigationItem({
        parentId: parentChoice === '' ? null : parentChoice,
        sortOrder: sort,
        routePath: route,
        label: label.trim(),
        description: description.trim() === '' ? null : description.trim(),
        type: navType,
        icon: icon.trim() === '' ? null : icon.trim(),
        categoryKey: categoryKey.trim() === '' ? null : categoryKey.trim(),
        searchKeywords: parseTokenTextarea(searchKeywordsText),
        requiredPermissions: parseTokenTextarea(requiredPermissionsText),
        requiredRoles: parseTokenTextarea(requiredRolesText),
        scope: createScope,
      });
      clearPortalNavigationCache();
      closeModal();
      await load();
    } catch (e) {
      setFormError(e instanceof Error ? e.message : 'Create failed');
    } finally {
      setSaving(false);
    }
  };

  const submitEdit = async () => {
    if (!editing) return;
    setFormError(null);
    const sort = parseInt(sortOrder, 10);
    if (Number.isNaN(sort)) {
      setFormError('Sort order must be a number.');
      return;
    }
    if (!label.trim()) {
      setFormError('Label is required.');
      return;
    }

    setSaving(true);
    try {
      const body: NavigationItemPatchRequest = {
        type: navType,
        sortOrder: sort,
        label: label.trim(),
        description: description.trim(),
        icon: icon.trim() === '' ? null : icon.trim(),
        categoryKey: categoryKey.trim() === '' ? null : categoryKey.trim(),
        searchKeywords: parseTokenTextarea(searchKeywordsText),
        requiredPermissions: parseTokenTextarea(requiredPermissionsText),
        requiredRoles: parseTokenTextarea(requiredRolesText),
        active,
      };

      const rt = routePath.trim();
      if (navType === 'internal') {
        if (rt !== '') {
          if (!isAllowedInternalSpaRouteClient(rt)) {
            setFormError(
              'Internal type: use /home, /entities, /audit, /entities/{uuid}/audit, or /entities/{uuid}/records (or …/records/new; records may use allowed query params).'
            );
            setSaving(false);
            return;
          }
          body.routePath = rt;
        }
      } else if (navType === 'external') {
        if (rt !== '') {
          body.routePath = rt;
        }
      } else {
        /* section / divider: send path if set, or empty string to clear a previous internal URL */
        body.routePath = rt;
      }

      if (parentChoice !== PARENT_KEEP) {
        body.parentId = parentChoice;
      }

      if (canManageGlobalNavigation && editing.tenantId && promoteToGlobal) {
        body.promoteToGlobal = true;
      }

      await patchNavigationItem(editing.id, body);
      clearPortalNavigationCache();
      closeModal();
      await load();
    } catch (e) {
      setFormError(e instanceof Error ? e.message : 'Save failed');
    } finally {
      setSaving(false);
    }
  };

  if (!allowed) {
    return (
      <div className="page-shell">
        <h1 className="page-title">Portal navigation</h1>
        <p className="page-sub">You do not have permission to manage portal navigation items.</p>
        <p className="builder-muted" style={{ marginTop: 12 }}>
          <Link to="/home">Back to home</Link>
        </p>
      </div>
    );
  }

  const modalOpen = modalMode !== 'closed';

  return (
    <div className="page-shell page-shell-wide">
      <nav className="breadcrumb">
        <Link to="/home">Home</Link>
        <span aria-hidden> / </span>
        <span>Navigation items</span>
      </nav>
      <header className="page-header">
        <div>
          <h1 className="page-title">Portal navigation items</h1>
          <p className="page-sub">Search, add, and edit entries you are allowed to manage.</p>
        </div>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          <button type="button" className="btn btn-primary btn-sm" onClick={openCreate}>
            Add item
          </button>
          <button type="button" className="btn btn-secondary btn-sm" onClick={() => void load()}>
            Reload
          </button>
        </div>
      </header>

      <div style={{ marginBottom: 16, display: 'flex', flexWrap: 'wrap', gap: 12, alignItems: 'center' }}>
        <label className="field-label" style={{ margin: 0, flex: '1 1 240px', minWidth: 200 }}>
          <span className="builder-muted" style={{ fontSize: '0.8125rem' }}>
            Search
          </span>
          <input
            className="input"
            placeholder="Label, description, route, category, keywords…"
            value={searchDraft}
            onChange={(e) => setSearchDraft(e.target.value.slice(0, 200))}
            aria-label="Search navigation items"
          />
        </label>
      </div>

      {loadError && (
        <p role="alert" className="text-error">
          {loadError}
        </p>
      )}

      <div className="records-table-wrap">
        <table className="records-table">
          <thead>
            <tr>
              <th aria-label="Icon" />
              <th>Label</th>
              <th>Type</th>
              <th>Route</th>
              <th>Category</th>
              <th>Sort</th>
              <th>Scope</th>
              <th>Active</th>
              <th>Id</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {filtered.map((row) => (
              <tr key={row.id}>
                <td style={{ width: 36, verticalAlign: 'middle' }}>
                  <PortalNavIcon name={row.icon} className="portal-nav-icon" />
                </td>
                <td>{row.label}</td>
                <td>
                  <code>{row.type}</code>
                </td>
                <td>
                  <span style={{ wordBreak: 'break-all' }}>{row.routePath ?? '—'}</span>
                </td>
                <td>{row.categoryKey ?? '—'}</td>
                <td>{row.sortOrder}</td>
                <td>{row.tenantId ? 'Tenant' : 'Global'}</td>
                <td>{row.active ? 'Yes' : 'No'}</td>
                <td>
                  <code>{shortId(row.id)}</code>
                </td>
                <td>
                  <button type="button" className="btn btn-secondary btn-sm" onClick={() => openEdit(row)}>
                    Edit
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {items && items.length === 0 && !loadError && (
        <p className="builder-muted" style={{ marginTop: 12 }}>
          No navigation items returned for your account.
        </p>
      )}
      {items && filtered.length === 0 && items.length > 0 && (
        <p className="builder-muted" style={{ marginTop: 12 }}>
          No rows match the current search.
        </p>
      )}

      {modalOpen && (
        <Modal
          wide
          title={modalMode === 'create' ? 'Add navigation item' : `Edit: ${editing?.label ?? ''}`}
          onClose={closeModal}
          footer={
            <>
              <button type="button" className="btn btn-secondary" onClick={closeModal} disabled={saving}>
                Cancel
              </button>
              <button
                type="button"
                className="btn btn-primary"
                onClick={() => void (modalMode === 'create' ? submitCreate() : submitEdit())}
                disabled={saving}
              >
                {saving ? (modalMode === 'create' ? 'Creating…' : 'Saving…') : modalMode === 'create' ? 'Create' : 'Save'}
              </button>
            </>
          }
        >
          {formError && (
            <p role="alert" className="text-error" style={{ marginTop: 0 }}>
              {formError}
            </p>
          )}
          {modalMode === 'edit' && editing && (
            <p className="builder-muted" style={{ marginTop: 0, fontSize: '0.8125rem' }}>
              Id <code>{editing.id}</code>
            </p>
          )}
          <p className="builder-muted" style={{ marginTop: 0, fontSize: '0.8125rem' }}>
            <strong>Type</strong> controls how the shell renders the row. Use <strong>internal</strong> for in-app links
            (<code>/home</code>, <code>/entities</code>, <code>/audit</code>, <code>/entities/…/audit</code>, or entity records URLs). External opens in a new tab; section and
            divider may leave route empty.
          </p>
          <div style={{ display: 'grid', gap: 12 }}>
            {modalMode === 'create' && canManageGlobalNavigation && (
              <label className="field-label">
                Scope
                <select
                  className="input"
                  value={createScope}
                  onChange={(e) => setCreateScope(e.target.value as 'TENANT' | 'GLOBAL')}
                  aria-label="Navigation item scope"
                >
                  <option value="TENANT">Tenant (current tenant)</option>
                  <option value="GLOBAL">Global (all tenants)</option>
                </select>
              </label>
            )}
            <label className="field-label">
              Parent
              <select
                className="input"
                value={parentChoice}
                onChange={(e) => setParentChoice(e.target.value)}
                aria-label="Parent item"
              >
                {modalMode === 'create' ? (
                  <option value="">— Root —</option>
                ) : (
                  <option value={PARENT_KEEP}>Keep current parent</option>
                )}
                {parentOptions.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.label} ({shortId(p.id)})
                  </option>
                ))}
              </select>
            </label>
            <label className="field-label">
              Sort order
              <input
                className="input"
                type="number"
                value={sortOrder}
                onChange={(e) => setSortOrder(e.target.value)}
              />
            </label>
            <label className="field-label">
              Type
              <select
                className="input"
                value={navType}
                onChange={(e) => setNavType(e.target.value)}
                aria-label="Navigation item type"
              >
                {NAV_ITEM_TYPES.map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </select>
            </label>
            {modalMode === 'create' && navType === 'internal' && (
              <>
                <label className="field-label">
                  Entity (sets route)
                  <select
                    className="input"
                    value={entityPickerId}
                    onChange={(e) => {
                      const id = e.target.value;
                      setEntityPickerId(id);
                      if (id) applyEntityRoute(id, entityRouteTarget);
                    }}
                    aria-label="Pick entity for route"
                  >
                    <option value="">— Select entity —</option>
                    {entitiesPickList?.map((e) => (
                      <option key={e.id} value={e.id}>
                        {e.name} ({e.slug})
                      </option>
                    ))}
                  </select>
                </label>
                <label className="field-label">
                  Entity route target
                  <select
                    className="input"
                    value={entityRouteTarget}
                    onChange={(e) => {
                      const t = e.target.value as 'list' | 'new';
                      setEntityRouteTarget(t);
                      if (entityPickerId) applyEntityRoute(entityPickerId, t);
                    }}
                    aria-label="Records list or new record"
                  >
                    <option value="list">Records list</option>
                    <option value="new">New record</option>
                  </select>
                </label>
              </>
            )}
            <label className="field-label">
              Route path
              <input
                className="input"
                value={routePath}
                onChange={(e) => setRoutePath(e.target.value)}
                placeholder={
                  navType === 'internal'
                    ? '/home, /entities, /audit, /entities/{uuid}/audit, or /entities/{uuid}/records…'
                    : navType === 'external'
                      ? 'https://…'
                      : 'Optional for section / divider'
                }
              />
            </label>
            <label className="field-label">
              Label
              <input className="input" value={label} onChange={(e) => setLabel(e.target.value)} />
            </label>
            <label className="field-label">
              Description
              <input className="input" value={description} onChange={(e) => setDescription(e.target.value)} />
            </label>
            <label className="field-label">
              Icon
              <div style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
                <div style={{ width: 22, display: 'flex', justifyContent: 'center', flexShrink: 0 }} aria-hidden>
                  {icon.trim() ? (
                    <PortalNavIcon name={icon} className="portal-nav-icon" />
                  ) : (
                    <span className="builder-muted">—</span>
                  )}
                </div>
                <select
                  className="input"
                  style={{ flex: '1 1 200px', minWidth: 160 }}
                  value={icon}
                  onChange={(e) => setIcon(e.target.value)}
                  aria-label="Navigation icon"
                >
                  <option value="">— None —</option>
                  {PORTAL_NAV_ICON_OPTIONS.map((o) => (
                    <option key={o.key} value={o.key}>
                      {o.label}
                    </option>
                  ))}
                  {iconIsLegacy && (
                    <option value={icon.trim()}>Legacy key: {icon.trim()}</option>
                  )}
                </select>
              </div>
              <span className="builder-muted" style={{ fontWeight: 400, marginTop: 4 }}>
                Stored as a short key (e.g. <code>layers</code>); the shell renders SVGs for known keys.
              </span>
            </label>
            <label className="field-label">
              Category key
              <select
                className="input"
                value={categoryKey}
                onChange={(e) => setCategoryKey(e.target.value)}
                aria-label="Category key"
              >
                <option value="">— None —</option>
                {categoryKeySelectOptions.map((k) => (
                  <option key={k} value={k}>
                    {formatCategoryKeyLabel(k)} ({k})
                  </option>
                ))}
              </select>
              <span className="builder-muted" style={{ fontWeight: 400, marginTop: 4 }}>
                Vocabulary matches entity-builder <code>EntityCategoryKeys</code> (no dedicated API yet).
              </span>
            </label>
            <label className="field-label">
              Search keywords (comma or newline separated)
              <textarea className="input" rows={3} value={searchKeywordsText} onChange={(e) => setSearchKeywordsText(e.target.value)} />
            </label>
            <label className="field-label">
              Required permissions
              <textarea
                className="input"
                rows={2}
                value={requiredPermissionsText}
                onChange={(e) => setRequiredPermissionsText(e.target.value)}
              />
            </label>
            <label className="field-label">
              Required roles
              <textarea className="input" rows={2} value={requiredRolesText} onChange={(e) => setRequiredRolesText(e.target.value)} />
            </label>
            {modalMode === 'edit' && (
              <>
                <label className="field-label row" style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
                  <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
                  <span>Active</span>
                </label>
                {canManageGlobalNavigation && editing?.tenantId ? (
                  <label className="field-label row" style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
                    <input type="checkbox" checked={promoteToGlobal} onChange={(e) => setPromoteToGlobal(e.target.checked)} />
                    <span>Promote to global</span>
                  </label>
                ) : null}
              </>
            )}
          </div>
        </Modal>
      )}
    </div>
  );
}

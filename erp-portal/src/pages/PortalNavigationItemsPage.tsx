import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Anchor,
  Button,
  Checkbox,
  Code,
  Group,
  Select,
  Stack,
  Text,
  Textarea,
  TextInput,
  Title,
} from '@mantine/core';
import type { ColumnDef } from '@tanstack/react-table';
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
import { DataTable } from '../components/DataTable';
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

const DESIGN_STATUS_OPTIONS: { value: 'PUBLISHED' | 'WIP'; label: string }[] = [
  { value: 'PUBLISHED', label: 'Published (live in shell)' },
  { value: 'WIP', label: 'WIP (draft — shows indicator in nav)' },
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
  const [designStatus, setDesignStatus] = useState<'PUBLISHED' | 'WIP'>('PUBLISHED');

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
    setDesignStatus('PUBLISHED');
  };

  const openEdit = useCallback((row: NavigationAdminItemDto) => {
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
    setDesignStatus(row.designStatus === 'WIP' ? 'WIP' : 'PUBLISHED');
  }, []);

  const navColumns = useMemo<ColumnDef<NavigationAdminItemDto>[]>(
    () => [
      {
        id: 'icon',
        header: '',
        meta: { thStyle: { width: 44 }, tdStyle: { verticalAlign: 'middle' } },
        cell: ({ row }) => <PortalNavIcon name={row.original.icon} className="portal-nav-icon" />,
      },
      { accessorKey: 'label', header: 'Label' },
      {
        id: 'type',
        header: 'Type',
        cell: ({ row }) => <Code fz="sm">{row.original.type}</Code>,
      },
      {
        id: 'route',
        header: 'Route',
        cell: ({ row }) => (
          <span style={{ wordBreak: 'break-all' }}>{row.original.routePath ?? '—'}</span>
        ),
      },
      {
        id: 'category',
        header: 'Category',
        cell: ({ row }) => row.original.categoryKey ?? '—',
      },
      { accessorKey: 'sortOrder', header: 'Sort' },
      {
        id: 'scope',
        header: 'Scope',
        cell: ({ row }) => (row.original.tenantId ? 'Tenant' : 'Global'),
      },
      {
        id: 'active',
        header: 'Active',
        cell: ({ row }) => (row.original.active ? 'Yes' : 'No'),
      },
      {
        id: 'design',
        header: 'Design',
        cell: ({ row }) => (row.original.designStatus === 'WIP' ? 'WIP' : 'Published'),
      },
      {
        id: 'rowId',
        header: 'Id',
        cell: ({ row }) => <Code fz="sm">{shortId(row.original.id)}</Code>,
      },
      {
        id: 'actions',
        header: '',
        cell: ({ row }) => (
          <Button size="xs" variant="default" onClick={() => openEdit(row.original)}>
            Edit
          </Button>
        ),
      },
    ],
    [openEdit]
  );

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
        designStatus,
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
        designStatus,
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
    <Stack gap="lg" className="page-shell page-shell-wide" maw={1400}>
      <Group gap="xs" wrap="wrap">
        <Anchor component={Link} to="/home" size="sm">
          Home
        </Anchor>
        <Text span c="dimmed" aria-hidden>
          /
        </Text>
        <Text span size="sm">
          Navigation items
        </Text>
      </Group>

      <Group justify="space-between" align="flex-start" wrap="wrap" gap="md">
        <Stack gap={4}>
          <Title order={1} size="h2">
            Portal navigation items
          </Title>
          <Text c="dimmed" size="sm">
            Search, add, and edit entries you are allowed to manage.
          </Text>
        </Stack>
        <Group gap="xs">
          <Button size="sm" onClick={openCreate}>
            Add item
          </Button>
          <Button size="sm" variant="default" onClick={() => void load()}>
            Reload
          </Button>
        </Group>
      </Group>

      <TextInput
        label="Search"
        placeholder="Label, description, route, category, keywords…"
        value={searchDraft}
        onChange={(e) => setSearchDraft(e.target.value.slice(0, 200))}
        aria-label="Search navigation items"
        style={{ flex: '1 1 240px', maxWidth: 480 }}
        size="sm"
      />

      {loadError && (
        <Text role="alert" c="red" size="sm">
          {loadError}
        </Text>
      )}

      <DataTable data={filtered} columns={navColumns} getRowId={(r) => r.id} minWidth={720} />

      {items && items.length === 0 && !loadError && (
        <Text size="sm" c="dimmed">
          No navigation items returned for your account.
        </Text>
      )}
      {items && filtered.length === 0 && items.length > 0 && (
        <Text size="sm" c="dimmed">
          No rows match the current search.
        </Text>
      )}

      {modalOpen && (
        <Modal
          wide
          title={modalMode === 'create' ? 'Add navigation item' : `Edit: ${editing?.label ?? ''}`}
          onClose={closeModal}
          footer={
            <Group justify="flex-end" gap="sm">
              <Button variant="default" onClick={closeModal} disabled={saving}>
                Cancel
              </Button>
              <Button
                onClick={() => void (modalMode === 'create' ? submitCreate() : submitEdit())}
                disabled={saving}
              >
                {saving ? (modalMode === 'create' ? 'Creating…' : 'Saving…') : modalMode === 'create' ? 'Create' : 'Save'}
              </Button>
            </Group>
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
          <Stack gap="sm">
            {modalMode === 'create' && canManageGlobalNavigation && (
              <Select
                label="Scope"
                value={createScope}
                onChange={(v) => v && setCreateScope(v as 'TENANT' | 'GLOBAL')}
                aria-label="Navigation item scope"
                data={[
                  { value: 'TENANT', label: 'Tenant (current tenant)' },
                  { value: 'GLOBAL', label: 'Global (all tenants)' },
                ]}
              />
            )}
            <Select
              label="Parent"
              value={parentChoice}
              onChange={(v) => setParentChoice(v ?? '')}
              aria-label="Parent item"
              data={[
                ...(modalMode === 'create'
                  ? [{ value: '', label: '— Root —' }]
                  : [{ value: PARENT_KEEP, label: 'Keep current parent' }]),
                ...parentOptions.map((p) => ({ value: p.id, label: `${p.label} (${shortId(p.id)})` })),
              ]}
            />
            <TextInput
              label="Sort order"
              type="number"
              value={sortOrder}
              onChange={(e) => setSortOrder(e.target.value)}
            />
            <Select
              label="Type"
              value={navType}
              onChange={(v) => v && setNavType(v)}
              aria-label="Navigation item type"
              data={NAV_ITEM_TYPES.map((o) => ({ value: o.value, label: o.label }))}
            />
            {modalMode === 'create' && navType === 'internal' && (
              <>
                <Select
                  label="Entity (sets route)"
                  placeholder="— Select entity —"
                  value={entityPickerId || null}
                  onChange={(id) => {
                    setEntityPickerId(id ?? '');
                    if (id) applyEntityRoute(id, entityRouteTarget);
                  }}
                  aria-label="Pick entity for route"
                  data={(entitiesPickList ?? []).map((e) => ({
                    value: e.id,
                    label: `${e.name} (${e.slug})`,
                  }))}
                />
                <Select
                  label="Entity route target"
                  value={entityRouteTarget}
                  onChange={(t) => {
                    const next = (t ?? 'list') as 'list' | 'new';
                    setEntityRouteTarget(next);
                    if (entityPickerId) applyEntityRoute(entityPickerId, next);
                  }}
                  aria-label="Records list or new record"
                  data={[
                    { value: 'list', label: 'Records list' },
                    { value: 'new', label: 'New record' },
                  ]}
                />
              </>
            )}
            <TextInput
              label="Route path"
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
            <TextInput label="Label" value={label} onChange={(e) => setLabel(e.target.value)} />
            <TextInput label="Description" value={description} onChange={(e) => setDescription(e.target.value)} />
            <div>
              <Text size="sm" fw={500} mb={6}>
                Icon
              </Text>
              <Group gap="sm" align="center" wrap="wrap">
                <div style={{ width: 22, display: 'flex', justifyContent: 'center', flexShrink: 0 }} aria-hidden>
                  {icon.trim() ? (
                    <PortalNavIcon name={icon} className="portal-nav-icon" />
                  ) : (
                    <span className="builder-muted">—</span>
                  )}
                </div>
                <Select
                  style={{ flex: '1 1 200px', minWidth: 160 }}
                  placeholder="— None —"
                  value={icon || null}
                  onChange={(v) => setIcon(v ?? '')}
                  aria-label="Navigation icon"
                  clearable
                  data={[
                    ...PORTAL_NAV_ICON_OPTIONS.map((o) => ({ value: o.key, label: o.label })),
                    ...(iconIsLegacy && icon.trim()
                      ? [{ value: icon.trim(), label: `Legacy key: ${icon.trim()}` }]
                      : []),
                  ]}
                />
              </Group>
              <Text size="xs" c="dimmed" mt={4}>
                Stored as a short key (e.g. <code>layers</code>); the shell renders SVGs for known keys.
              </Text>
            </div>
            <div>
              <Select
                label="Category key"
                placeholder="— None —"
                value={categoryKey || null}
                onChange={(v) => setCategoryKey(v ?? '')}
                aria-label="Category key"
                clearable
                data={categoryKeySelectOptions.map((k) => ({
                  value: k,
                  label: `${formatCategoryKeyLabel(k)} (${k})`,
                }))}
              />
              <Text size="xs" c="dimmed" mt={4}>
                Vocabulary matches entity-builder <code>EntityCategoryKeys</code> (no dedicated API yet).
              </Text>
            </div>
            <Textarea
              label="Search keywords (comma or newline separated)"
              rows={3}
              value={searchKeywordsText}
              onChange={(e) => setSearchKeywordsText(e.target.value)}
            />
            <Textarea
              label="Required permissions"
              rows={2}
              value={requiredPermissionsText}
              onChange={(e) => setRequiredPermissionsText(e.target.value)}
            />
            <Textarea
              label="Required roles"
              rows={2}
              value={requiredRolesText}
              onChange={(e) => setRequiredRolesText(e.target.value)}
            />
            <Select
              label="Design status"
              value={designStatus}
              onChange={(v) => v && setDesignStatus(v as 'PUBLISHED' | 'WIP')}
              aria-label="Navigation item design status"
              data={DESIGN_STATUS_OPTIONS.map((o) => ({ value: o.value, label: o.label }))}
            />
            {modalMode === 'edit' && (
              <>
                <Checkbox checked={active} onChange={(e) => setActive(e.currentTarget.checked)} label="Active" />
                {canManageGlobalNavigation && editing?.tenantId ? (
                  <Checkbox
                    checked={promoteToGlobal}
                    onChange={(e) => setPromoteToGlobal(e.currentTarget.checked)}
                    label="Promote to global"
                  />
                ) : null}
              </>
            )}
          </Stack>
        </Modal>
      )}
    </Stack>
  );
}

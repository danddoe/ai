import { Button, Checkbox, Select, TextInput } from '@mantine/core';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  ApiHttpError,
  createFormLayout,
  createNavigationItem,
  createRecordListView,
  getRecordListView,
  listEntities,
  listFields,
  listFormLayouts,
  listNavigationAdminItems,
  listRecordListViews,
  patchFormLayout,
  type EntityDto,
  type EntityFieldDto,
  type NavigationAdminItemDto,
  type NavigationItemDto,
  type RecordListViewDto,
} from '../api/schemas';
import { useAuth } from '../auth/AuthProvider';
import { clearPortalNavigationCache, usePortalNavigation } from '../hooks/usePortalNavigation';
import type { LayoutV2 } from '../types/formLayout';
import { buildAutoFormLayoutV2, suggestListColumnSlugs } from '../ui/createUiArtifacts';
import {
  UI_TEMPLATES,
  type UiTemplateId,
  requiredPermissionsForTemplate,
  routePathForTemplate,
  searchKeywordsForEntity,
} from '../ui/uiTemplates';
import { parseLayoutV2 } from '../utils/layoutV2';
import {
  buildRecordListViewDefinitionV1,
  parseRecordListViewDefinition,
} from '../utils/recordListViewDefinition';
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

/**
 * Sections for parent picker when user can call {@link listNavigationAdminItems}. Uses the full nav tree so
 * sections are not dropped when all children are hidden by permission (see IAM {@code toDto} empty-section rule).
 */
function collectSectionParentsFromAdminList(items: NavigationAdminItemDto[]): { id: string; label: string }[] {
  const active = items.filter((i) => i.active);
  const childrenByParent = new Map<string | null, NavigationAdminItemDto[]>();
  for (const i of active) {
    const p = i.parentId;
    const list = childrenByParent.get(p) ?? [];
    list.push(i);
    childrenByParent.set(p, list);
  }
  for (const list of childrenByParent.values()) {
    list.sort((a, b) => a.sortOrder - b.sortOrder || a.id.localeCompare(b.id));
  }
  const acc: { id: string; label: string }[] = [];
  function walk(parentId: string | null, depth: number) {
    const kids = childrenByParent.get(parentId) ?? [];
    for (const n of kids) {
      if (n.type === 'section') {
        acc.push({ id: n.id, label: `${'\u00A0\u00A0'.repeat(depth)}${n.label}` });
      }
      walk(n.id, depth + 1);
    }
  }
  walk(null, 0);
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
  const listColSeedEntityRef = useRef<string | null>(null);

  const colStep = columnStepEnabled(template);
  const navStepNum = colStep ? 4 : 3;
  const reviewStepNum = colStep ? 5 : 4;

  /** When set (including []), parent-section options come from admin list; otherwise from visible nav tree. */
  const [adminSectionParents, setAdminSectionParents] = useState<{ id: string; label: string }[] | undefined>(undefined);

  useEffect(() => {
    if (!canCreatePortalNavItem) {
      setAdminSectionParents(undefined);
      return;
    }
    let cancelled = false;
    void listNavigationAdminItems()
      .then((r) => {
        if (!cancelled) setAdminSectionParents(collectSectionParentsFromAdminList(r.items));
      })
      .catch(() => {
        if (!cancelled) setAdminSectionParents(undefined);
      });
    return () => {
      cancelled = true;
    };
  }, [canCreatePortalNavItem]);

  const sectionParents = useMemo(() => {
    if (adminSectionParents !== undefined) return adminSectionParents;
    return collectSectionParents(navRoots);
  }, [adminSectionParents, navRoots]);

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
    listColSeedEntityRef.current = null;
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

  useEffect(() => {
    if (!canSchemaWrite || !entityId || !listFieldsState?.length || step !== 3 || !colStep) return;
    if (listColSeedEntityRef.current === entityId) return;
    listColSeedEntityRef.current = entityId;
    setSelectedColSlugs(suggestListColumnSlugs(listFieldsState));
  }, [canSchemaWrite, entityId, listFieldsState, step, colStep]);

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

  async function createListViewWipUnique(entityIdParam: string, baseName: string, definition: Record<string, unknown>) {
    let name = baseName.slice(0, 255);
    for (let i = 0; i < 5; i++) {
      try {
        return await createRecordListView(entityIdParam, {
          name,
          definition,
          isDefault: false,
          status: 'WIP',
        });
      } catch (e) {
        if (e instanceof ApiHttpError && e.status === 409 && i < 4) {
          name = `${baseName.slice(0, 220)} (${i + 2})`.slice(0, 255);
          continue;
        }
        throw e;
      }
    }
    throw new Error('Could not allocate a unique list view name');
  }

  async function createFormLayoutWipUnique(
    entityIdParam: string,
    baseName: string,
    layout: LayoutV2,
    isDefault: boolean
  ) {
    let name = baseName.slice(0, 255);
    for (let i = 0; i < 5; i++) {
      try {
        return await createFormLayout(entityIdParam, {
          name,
          layout,
          isDefault,
          status: 'WIP',
        });
      } catch (e) {
        if (e instanceof ApiHttpError && e.status === 409 && i < 4) {
          name = `${baseName.slice(0, 220)} (${i + 2})`.slice(0, 255);
          continue;
        }
        throw e;
      }
    }
    throw new Error('Could not allocate a unique form layout name');
  }

  async function submitLegacy() {
    if (!entityId || !label.trim() || !previewPath) return;
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
  }

  async function submitWithWipDesigners() {
    if (!entityId || !label.trim()) return;

    const fields = await listFields(entityId);
    const layouts = await listFormLayouts(entityId);
    const hasDefaultLayout = layouts.some((l) => l.isDefault);
    const setDefaultForm = !hasDefaultLayout;

    let listViewId: string | null = null;
    let formLayoutId: string | null = null;
    let routePath: string;

    if (template === 'form_landing') {
      if (fields.length === 0) {
        throw new Error('This entity has no fields. Add fields before creating a form.');
      }
      const layout = buildAutoFormLayoutV2(fields, 'single_page');
      const formDto = await createFormLayoutWipUnique(entityId, `${label.trim()} form`, layout, setDefaultForm);
      formLayoutId = formDto.id;
      routePath = routePathForTemplate('form_landing', entityId);
    } else {
      let colSlugs: string[] = [];
      if (listMode === 'saved' && savedViewId.trim()) {
        const dto = await getRecordListView(entityId, savedViewId.trim());
        const def = parseRecordListViewDefinition(dto.definition);
        if (def?.columns?.length) {
          colSlugs = [...def.columns]
            .filter((c) => c.visible !== false)
            .sort((a, b) => a.order - b.order)
            .map((c) => c.fieldSlug);
        }
      }
      if (colSlugs.length === 0) {
        colSlugs = selectedColSlugs.length > 0 ? selectedColSlugs : suggestListColumnSlugs(fields);
      }
      if (colSlugs.length === 0) {
        throw new Error('No list columns could be suggested. Add fields or pick columns in step 3.');
      }
      const listDef = buildRecordListViewDefinitionV1(
        colSlugs.map((slug, i) => ({
          fieldSlug: slug,
          order: i,
          inlineEditable: inlineColSlugs.includes(slug),
          visible: true,
        })),
        showRowActions
      );
      const listDto = await createListViewWipUnique(
        entityId,
        `${label.trim()} list`,
        listDef as unknown as Record<string, unknown>
      );
      listViewId = listDto.id;
      routePath = recordsListPath(entityId, {
        viewId: listViewId,
        cols: [],
        inlineSlugs: [],
        showRowActions: true,
      });

      if (template === 'list_and_form') {
        if (fields.length === 0) {
          throw new Error('This entity has no fields. Add fields before creating a form.');
        }
        const layout = buildAutoFormLayoutV2(fields, 'single_page');
        const formDto = await createFormLayoutWipUnique(entityId, `${label.trim()} form`, layout, setDefaultForm);
        formLayoutId = formDto.id;
      } else if (template === 'multi_step') {
        if (fields.length === 0) {
          throw new Error('This entity has no fields. Add fields before creating a multi-step form.');
        }
        const layout = buildAutoFormLayoutV2(fields, 'wizard');
        const formDto = await createFormLayoutWipUnique(entityId, `${label.trim()} form`, layout, setDefaultForm);
        formLayoutId = formDto.id;
      }
    }

    const { id: navId } = await createNavigationItem({
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
      designStatus: 'WIP',
      linkedListViewId: listViewId ?? undefined,
      linkedFormLayoutId: formLayoutId ?? undefined,
    });
    clearPortalNavigationCache();
    await loadNav();

    if (listViewId) {
      navigate(`/entities/${entityId}/list-views/${listViewId}`, {
        state: {
          linkedNavigationItemId: navId,
          linkedFormLayoutId: formLayoutId ?? undefined,
        },
      });
    } else if (formLayoutId) {
      navigate(`/entities/${entityId}/layouts/${formLayoutId}`, {
        state: {
          linkedNavigationItemId: navId,
          linkedListViewId: listViewId ?? undefined,
        },
      });
    } else {
      navigate(routePath);
    }
  }

  async function submit() {
    if (!entityId || !label.trim() || !previewPath) return;
    setBusy(true);
    setError(null);
    try {
      if (canSchemaWrite) {
        await submitWithWipDesigners();
      } else {
        await submitLegacy();
      }
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
          <Button type="button" mt="md" onClick={() => setStep(2)}>
            Next
          </Button>
        </section>
      )}

      {step === 2 && (
        <section>
          <h2 className="page-title" style={{ fontSize: '1.1rem' }}>
            Choose entity
          </h2>
          <TextInput
            placeholder="Search by name, slug, or id…"
            value={entitySearchInput}
            onChange={(e) => setEntitySearchInput(e.target.value)}
            maw={420}
            mb="sm"
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
            <Button type="button" variant="default" onClick={() => setStep(1)}>
              Back
            </Button>
            <Button type="button" disabled={!entityId} onClick={() => setStep(3)}>
              Next
            </Button>
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
              <Select
                label="List view"
                placeholder="— Select —"
                value={savedViewId || null}
                onChange={(v) => setSavedViewId(v ?? '')}
                data={(savedViews ?? []).map((v) => ({
                  value: v.id,
                  label: `${v.name}${v.isDefault ? ' (default)' : ''}`,
                }))}
              />
              {savedViews && savedViews.length === 0 && (
                <p className="builder-muted" style={{ fontSize: '0.875rem' }}>
                  No saved views yet — use “Create new saved view” or the records page designer.
                </p>
              )}
            </div>
          )}
          {listMode === 'create_saved' && canSchemaWrite && (
            <TextInput
              label="New view name"
              mb="md"
              maw={420}
              value={newListViewName}
              onChange={(e) => setNewListViewName(e.target.value)}
              placeholder="e.g. Sales pipeline"
            />
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
              <TextInput
                placeholder="Filter fields…"
                value={fieldSearch}
                onChange={(e) => setFieldSearch(e.target.value)}
                maw={420}
                mb="sm"
              />
              <Checkbox
                mb="sm"
                checked={showRowActions}
                onChange={(e) => setShowRowActions(e.currentTarget.checked)}
                label="Show row actions (Edit / Delete)"
              />
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
            <Button type="button" variant="default" onClick={() => setStep(2)}>
              Back
            </Button>
            <Button
              type="button"
              disabled={listMode === 'saved' && canSchemaWrite ? !savedViewId.trim() : false}
              onClick={() => setStep(4)}
            >
              Next
            </Button>
          </div>
        </section>
      )}

      {step === navStepNum && (
        <section>
          <h2 className="page-title" style={{ fontSize: '1.1rem' }}>
            Navigation entry
          </h2>
          <div style={{ display: 'grid', gap: 12, maxWidth: 480 }}>
            <TextInput label="Label" value={label} onChange={(e) => setLabel(e.target.value)} />
            <TextInput
              label="Description (optional)"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />
            <Select
              label="Parent (section)"
              placeholder="— None —"
              value={parentId || null}
              onChange={(v) => setParentId(v ?? '')}
              data={sectionParents.map((p) => ({ value: p.id, label: p.label }))}
            />
            {canManageGlobalNavigation && (
              <Checkbox
                checked={globalScope}
                onChange={(e) => setGlobalScope(e.currentTarget.checked)}
                label="Global navigation (all tenants, subject to permissions)"
              />
            )}
          </div>
          <div style={{ marginTop: 16, display: 'flex', gap: 8 }}>
            <Button type="button" variant="default" onClick={() => setStep(colStep ? 3 : 2)}>
              Back
            </Button>
            <Button type="button" disabled={!label.trim()} onClick={() => setStep(reviewStepNum)}>
              Next
            </Button>
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
            {template === 'multi_step' && !canSchemaWrite && (
              <li>
                <strong>Layout:</strong> default layout will be patched to wizard mode (region order).
              </li>
            )}
            {template === 'multi_step' && canSchemaWrite && (
              <li>
                <strong>Layout:</strong> a new draft multi-step form layout will be created in the form builder.
              </li>
            )}
            {canSchemaWrite && (
              <li>
                <strong>Design:</strong> sidebar link and related screens start as <strong>WIP</strong>; open the list/form
                designer, then use <strong>Publish</strong> when ready.
              </li>
            )}
            {!canSchemaWrite && (
              <li className="builder-muted">
                Without <code>entity_builder:schema:write</code>, a sidebar link opens the live app (no draft designers).
              </li>
            )}
          </ul>
          <div style={{ marginTop: 16, display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            <Button type="button" variant="default" onClick={() => setStep(navStepNum)} disabled={busy}>
              Back
            </Button>
            <Button type="button" disabled={busy} onClick={() => void submit()}>
              {busy ? 'Creating…' : canSchemaWrite ? 'Create draft & open designer' : 'Create & open'}
            </Button>
          </div>
        </section>
      )}

      <p className="builder-muted" style={{ marginTop: 24 }}>
        <Link to="/entities">← Entities</Link>
      </p>
    </div>
  );
}

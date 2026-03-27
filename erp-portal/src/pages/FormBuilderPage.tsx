import { Button, Checkbox, TextInput } from '@mantine/core';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import {
  getEntity,
  getFormLayout,
  listEntities,
  listEntityRelationships,
  listFields,
  patchFormLayout,
  type EntityDto,
  type EntityFieldDto,
  type EntityRelationshipDto,
  type FormLayoutDto,
} from '../api/schemas';
import { useAuth } from '../auth/AuthProvider';
import { canMutateEntityDefinition } from '../auth/jwtPermissions';
import { FieldsPanel } from '../components/builder/FieldsPanel';
import { StructurePanel } from '../components/builder/StructurePanel';
import { PropertiesPanel } from '../components/builder/PropertiesPanel';
import { CreateFieldModal } from '../components/builder/CreateFieldModal';
import { EditFieldModal } from '../components/builder/EditFieldModal';
import { TemplatePickerModal } from '../components/builder/TemplatePickerModal';
import { publishDesignArtifacts } from '../ui/publishDesignArtifacts';
import type { LayoutV2, StructureSelection } from '../types/formLayout';
import { fieldOnForm, newLayoutActionItem, newLayoutItem, parseLayoutV2 } from '../utils/layoutV2';
import * as M from '../utils/layoutMutations';

export function FormBuilderPage() {
  const { entityId = '', layoutId = '' } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const { canSchemaWrite, permissions } = useAuth();

  const [entity, setEntity] = useState<EntityDto | null>(null);
  const [layoutDto, setLayoutDto] = useState<FormLayoutDto | null>(null);
  const [fields, setFields] = useState<EntityFieldDto[]>([]);
  const [draft, setDraft] = useState<LayoutV2 | null>(null);
  const [legacy, setLegacy] = useState(false);
  const [layoutName, setLayoutName] = useState('');
  /** Drives record entry (new/edit) and preview — only one default per entity. */
  const [layoutIsDefault, setLayoutIsDefault] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [search, setSearch] = useState('');
  const [filter, setFilter] = useState<'all' | 'on' | 'off'>('all');
  const [addTarget, setAddTarget] = useState<{
    regionIndex: number;
    rowIndex: number;
    columnIndex: number;
  } | null>(null);
  const [selection, setSelection] = useState<StructureSelection | null>(null);
  const [createFieldOpen, setCreateFieldOpen] = useState(false);
  const [createFieldCtx, setCreateFieldCtx] = useState<{
    suggestedName?: string;
    suggestedSlug?: string;
    bindSelection?: { regionIndex: number; rowIndex: number; columnIndex: number; itemIndex: number };
  } | null>(null);
  const [editField, setEditField] = useState<EntityFieldDto | null>(null);
  const [tplOpen, setTplOpen] = useState(false);
  const [relationships, setRelationships] = useState<EntityRelationshipDto[]>([]);
  const [allEntities, setAllEntities] = useState<EntityDto[]>([]);

  const canMutateFieldDefs = useMemo(
    () => (entity ? canMutateEntityDefinition(permissions, entity) : false),
    [entity, permissions]
  );

  const [linkedNavItemId, setLinkedNavItemId] = useState('');
  const [linkedListViewId, setLinkedListViewId] = useState('');

  useEffect(() => {
    const st = location.state as { linkedNavigationItemId?: string; linkedListViewId?: string } | undefined;
    setLinkedNavItemId(st?.linkedNavigationItemId ?? '');
    setLinkedListViewId(st?.linkedListViewId ?? '');
  }, [location.key, location.state]);

  const load = useCallback(async () => {
    if (!entityId || !layoutId) return;
    setError(null);
    setLegacy(false);
    try {
      const [e, f, l, rels, ents] = await Promise.all([
        getEntity(entityId),
        listFields(entityId),
        getFormLayout(entityId, layoutId),
        listEntityRelationships(),
        listEntities(),
      ]);
      setEntity(e);
      setFields(f);
      setLayoutDto(l);
      setRelationships(rels);
      setAllEntities(ents);
      setLayoutName(l.name);
      setLayoutIsDefault(l.isDefault);
      const parsed = parseLayoutV2(l.layout);
      if (!parsed) {
        setDraft(null);
        setLegacy(true);
      } else {
        setDraft(parsed);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load');
      setEntity(null);
      setDraft(null);
    }
  }, [entityId, layoutId]);

  useEffect(() => {
    void load();
  }, [load]);

  function onPickField(field: EntityFieldDto) {
    if (!draft || !addTarget) return;
    if (fieldOnForm(draft, field.id, field.slug)) {
      window.alert('This field is already on the form.');
      return;
    }
    const { regionIndex, rowIndex, columnIndex } = addTarget;
    setDraft(M.addItem(draft, regionIndex, rowIndex, columnIndex, newLayoutItem(field.id, field.slug)));
    setAddTarget(null);
  }

  async function save() {
    if (!draft || !layoutDto) return;
    setSaving(true);
    setError(null);
    try {
      const updated = await patchFormLayout(entityId, layoutId, {
        name: layoutName.trim() || layoutDto.name,
        layout: draft,
        isDefault: layoutIsDefault,
      });
      setLayoutDto(updated);
      setLayoutIsDefault(updated.isDefault);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Save failed');
    } finally {
      setSaving(false);
    }
  }

  const showWipPublish =
    canSchemaWrite && !!linkedNavItemId && layoutDto?.status === 'WIP' && !!layoutId;

  async function publishWip() {
    if (!linkedNavItemId || !entityId || !layoutId) return;
    setSaving(true);
    setError(null);
    try {
      await publishDesignArtifacts({
        entityId,
        navigationItemId: linkedNavItemId,
        listViewId: linkedListViewId || undefined,
        formLayoutId: layoutId,
      });
      const refreshed = await getFormLayout(entityId, layoutId);
      setLayoutDto(refreshed);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Publish failed');
    } finally {
      setSaving(false);
    }
  }

  if (legacy) {
    return (
      <div className="page-shell">
        <nav className="breadcrumb">
          <Link to="/entities">Entities</Link>
          <span aria-hidden> / </span>
          <Link to={`/entities/${entityId}/layouts`}>{entity?.name ?? 'Entity'}</Link>
        </nav>
        <div className="legacy-banner" role="alert">
          <h2 className="page-title">Legacy layout</h2>
          <p>
            This layout is not <code>version: 2</code> with regions. The builder only supports the v2 model. Create a
            new layout from a template or blank, then rebuild the structure.
          </p>
          <Button component={Link} to={`/entities/${entityId}/layouts`}>
            Back to layouts
          </Button>
        </div>
      </div>
    );
  }

  if (error && !draft) {
    return (
      <div className="page-shell">
        <p role="alert" className="text-error">
          {error}
        </p>
        <Link to={`/entities/${entityId}/layouts`}>← Layouts</Link>
      </div>
    );
  }

  if (!draft || !entity) {
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
          <span className="builder-muted">{layoutName || 'Layout'}</span>
        </nav>
        <div className="builder-topbar-actions">
          <span className="env-badge" title="API base">
            {import.meta.env.VITE_API_BASE_URL || 'http://localhost:8000'}
          </span>
          <Button
            type="button"
            variant="default"
            size="sm"
            onClick={() =>
              navigate(`/entities/${entityId}/layouts/${layoutId}/preview`, { state: { draft } })
            }
          >
            Preview
          </Button>
          {canSchemaWrite && (
            <Button type="button" variant="default" size="sm" onClick={() => setTplOpen(true)}>
              Start from template
            </Button>
          )}
          <Button type="button" disabled={saving || !canSchemaWrite} onClick={() => void save()}>
            {saving ? 'Saving…' : 'Save'}
          </Button>
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
          <strong>Work in progress</strong> — This form and the sidebar link are not published yet.{' '}
          {linkedListViewId ? (
            <>
              Open the{' '}
              <Link to={`/entities/${entityId}/list-views/${linkedListViewId}`}>list view designer</Link> if you need to
              adjust columns.
            </>
          ) : null}
          <Button type="button" size="sm" style={{ marginLeft: 12 }} disabled={saving} onClick={() => void publishWip()}>
            {saving ? 'Publishing…' : 'Publish'}
          </Button>
        </div>
      )}
      <div className="builder-name-row" style={{ display: 'flex', flexWrap: 'wrap', gap: 16, alignItems: 'flex-end' }}>
        <TextInput
          label="Layout name"
          style={{ flex: 1, maxWidth: 360 }}
          value={layoutName}
          onChange={(e) => setLayoutName(e.target.value)}
          readOnly={!canSchemaWrite}
        />
        <Checkbox
          checked={layoutIsDefault}
          onChange={(e) => setLayoutIsDefault(e.currentTarget.checked)}
          disabled={!canSchemaWrite}
          label="Default form layout for entity"
          title="Used for New record and Edit record (runtime form), not the list view"
        />
      </div>
      {error && (
        <p role="alert" className="text-error" style={{ margin: '0 1rem' }}>
          {error}
        </p>
      )}
      <div className="builder-grid">
        <FieldsPanel
          fields={fields}
          layout={draft}
          search={search}
          onSearchChange={setSearch}
          filter={filter}
          onFilterChange={setFilter}
          addTarget={addTarget}
          onClearAddTarget={() => setAddTarget(null)}
          onPickField={onPickField}
          onOpenCreateField={() => {
            if (!canMutateFieldDefs) return;
            setCreateFieldCtx({});
            setCreateFieldOpen(true);
          }}
          onOpenEditField={(f) => setEditField(f)}
          fieldDefinitionsWritable={canMutateFieldDefs}
        />
        <StructurePanel
          layout={draft}
          fields={fields}
          onChange={canSchemaWrite ? (next) => setDraft(next) : () => {}}
          selection={selection}
          onSelect={setSelection}
          onRequestAddField={setAddTarget}
          schemaWritable={canSchemaWrite}
          onAddActionItem={
            canSchemaWrite
              ? (target, action, linkHref) => {
                  if (!draft) return;
                  const { regionIndex, rowIndex, columnIndex } = target;
                  if (action === 'link') {
                    if (!linkHref?.trim()) return;
                    setDraft(
                      M.addItem(
                        draft,
                        regionIndex,
                        rowIndex,
                        columnIndex,
                        newLayoutActionItem('link', { href: linkHref.trim() })
                      )
                    );
                  } else {
                    setDraft(
                      M.addItem(draft, regionIndex, rowIndex, columnIndex, newLayoutActionItem(action))
                    );
                  }
                }
              : undefined
          }
        />
        <PropertiesPanel
          layout={draft}
          layoutEntityId={entity.id}
          selection={selection}
          fields={fields}
          relationships={relationships}
          allEntities={allEntities}
          schemaWritable={canSchemaWrite}
          onChange={canSchemaWrite ? (next) => setDraft(next) : () => {}}
          onClearSelection={() => setSelection(null)}
          onOpenCreateField={(opts) => {
            if (!canMutateFieldDefs) return;
            setCreateFieldCtx(opts ?? {});
            setCreateFieldOpen(true);
          }}
          onOpenEditField={(f) => setEditField(f)}
        />
      </div>
      {createFieldOpen && (
        <CreateFieldModal
          entityId={entityId}
          suggestedName={createFieldCtx?.suggestedName}
          suggestedSlug={createFieldCtx?.suggestedSlug}
          onClose={() => {
            setCreateFieldOpen(false);
            setCreateFieldCtx(null);
          }}
          onCreated={(f) => {
            setFields((prev) => [...prev, f]);
            const bind = createFieldCtx?.bindSelection;
            if (bind) {
              setDraft((prev) =>
                prev
                  ? M.bindItemToField(
                      prev,
                      bind.regionIndex,
                      bind.rowIndex,
                      bind.columnIndex,
                      bind.itemIndex,
                      f.id,
                      f.slug
                    )
                  : prev
              );
            }
            setCreateFieldCtx(null);
          }}
        />
      )}
      {editField && (
        <EditFieldModal
          key={editField.id}
          entityId={entityId}
          field={editField}
          onClose={() => setEditField(null)}
          onUpdated={(f) =>
            setFields((prev) => prev.map((x) => (x.id === f.id ? f : x)))
          }
          onDeleted={() => {
            setEditField(null);
            void load();
          }}
        />
      )}
      {tplOpen && (
        <TemplatePickerModal
          entityId={entityId}
          onClose={() => setTplOpen(false)}
          onCreated={(newId) => {
            setTplOpen(false);
            navigate(`/entities/${entityId}/layouts/${newId}`, { replace: true });
          }}
        />
      )}
    </div>
  );
}

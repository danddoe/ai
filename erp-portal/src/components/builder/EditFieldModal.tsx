import { FormEvent, useEffect, useMemo, useState } from 'react';
import {
  Button,
  Checkbox,
  Code,
  Divider,
  Group,
  MultiSelect,
  Paper,
  Select,
  Stack,
  Text,
  TextInput,
} from '@mantine/core';
import { Modal } from '../Modal';
import {
  DOCUMENT_NUMBER_FIELD_TYPE,
  activeEntityFields,
  createEntityRelationship,
  deleteEntityRelationship,
  deleteField,
  getEntity,
  isDocumentNumberFieldType,
  listEntities,
  listEntityRelationships,
  listFields,
  patchEntityRelationship,
  patchField,
  putFieldLabel,
  type DocumentNumberGenerationStrategy,
  type EntityDto,
  type EntityFieldDto,
  type EntityRelationshipDto,
} from '../../api/schemas';
import { useAuth } from '../../auth/AuthProvider';
import { canMutateEntityDefinition } from '../../auth/jwtPermissions';
import { DocumentNumberGenerationSection } from './DocumentNumberGenerationSection';
import {
  buildDocumentNumberGenerationPayload,
  readDocGenFromConfig,
} from './documentNumberGeneration';
import {
  applyTextLengthConstraintsToConfig,
  fieldTypeSupportsTextLengthConstraints,
  readConfigLengthString,
} from '../../utils/fieldTextConstraints';
import { MAX_REFERENCE_LOOKUP_COLUMNS } from '../../utils/referenceFieldConfig';
import { inverseCardinalityLabel, storageCardinalityAndDirection } from '../../utils/relationshipCardinality';
import { ENTITY_STATUS_ENTITY_SLUG } from '../../utils/entityStatusCatalog';
import { EntityStatusAssignmentsPanel } from '../EntityStatusAssignmentsPanel';

type Props = {
  entityId: string;
  field: EntityFieldDto;
  onClose: () => void;
  onUpdated: (f: EntityFieldDto) => void;
  /** Called after a successful DELETE (removed or deactivated); parent should refresh field lists. */
  onDeleted?: () => void;
};

function isReferenceFieldType(ft: string): boolean {
  return ft.trim().toLowerCase() === 'reference';
}

function readRelationshipId(cfg: EntityFieldDto['config']): string | undefined {
  const id = cfg && typeof cfg === 'object' && 'relationshipId' in cfg ? (cfg as { relationshipId?: unknown }).relationshipId : undefined;
  return typeof id === 'string' && id.trim() ? id.trim() : undefined;
}

function readTargetEntitySlug(cfg: EntityFieldDto['config']): string {
  const s = cfg && typeof cfg === 'object' && 'targetEntitySlug' in cfg ? (cfg as { targetEntitySlug?: unknown }).targetEntitySlug : undefined;
  return typeof s === 'string' ? s.trim() : '';
}

function readReferenceLookupDisplaySlugs(cfg: EntityFieldDto['config']): string[] {
  const raw =
    cfg && typeof cfg === 'object' && 'referenceLookupDisplaySlugs' in cfg
      ? (cfg as { referenceLookupDisplaySlugs?: unknown }).referenceLookupDisplaySlugs
      : undefined;
  if (!Array.isArray(raw)) return [];
  return raw
    .filter((s): s is string => typeof s === 'string' && s.trim().length > 0)
    .map((s) => s.trim())
    .slice(0, MAX_REFERENCE_LOOKUP_COLUMNS);
}

function readReferenceUiMode(cfg: EntityFieldDto['config']): 'search' | 'dropdown' {
  const raw =
    cfg && typeof cfg === 'object' && 'referenceUiMode' in cfg
      ? (cfg as { referenceUiMode?: unknown }).referenceUiMode
      : undefined;
  return raw === 'dropdown' ? 'dropdown' : 'search';
}

/**
 * Matches record links and form region bindings: {@code from} = parent entity (field owner), {@code to} = child entity.
 * Also recognizes legacy relationships created before that convention (inverted {@code from}/{@code to}).
 */
function findRelationshipForReferenceField(
  rels: EntityRelationshipDto[],
  p: { entityId: string; fieldSlug: string; targetEntityId: string | null; relationshipId?: string }
): EntityRelationshipDto | null {
  if (p.relationshipId) {
    const byId = rels.find((r) => r.id === p.relationshipId);
    if (byId) return byId;
  }
  if (!p.targetEntityId || !p.fieldSlug) return null;
  const strictCanonical = rels.find(
    (r) =>
      r.fromEntityId === p.entityId &&
      r.toEntityId === p.targetEntityId &&
      r.toFieldSlug === p.fieldSlug
  );
  if (strictCanonical) return strictCanonical;
  const looseCanonical =
    rels.find(
      (r) =>
        r.fromEntityId === p.entityId &&
        r.toEntityId === p.targetEntityId &&
        (r.toFieldSlug == null || r.toFieldSlug === '')
    ) ?? null;
  if (looseCanonical) return looseCanonical;
  const childSidePairs = rels.filter(
    (r) => r.fromEntityId === p.targetEntityId && r.toEntityId === p.entityId
  );
  if (childSidePairs.length === 1) return childSidePairs[0];
  const strictLegacy = rels.find(
    (r) =>
      r.fromEntityId === p.targetEntityId &&
      r.toEntityId === p.entityId &&
      r.toFieldSlug === p.fieldSlug
  );
  if (strictLegacy) return strictLegacy;
  return (
    rels.find(
      (r) =>
        r.fromEntityId === p.targetEntityId &&
        r.toEntityId === p.entityId &&
        (r.toFieldSlug == null || r.toFieldSlug === '')
    ) ?? null
  );
}

function suggestedRelationshipSlug(entitySlug: string, fieldSlug: string, targetSlug: string): string {
  const raw = `${entitySlug}_${fieldSlug}_to_${targetSlug}`;
  if (raw.length <= 100) return raw;
  return raw.slice(0, 100).replace(/_+$/g, '');
}

function configSearchable(config: EntityFieldDto['config']): boolean {
  return config?.isSearchable === true;
}

const EDITABLE_FIELD_TYPES: { value: string; label: string }[] = [
  { value: 'string', label: 'string' },
  { value: 'text', label: 'text' },
  { value: 'number', label: 'number' },
  { value: 'boolean', label: 'boolean' },
  { value: 'date', label: 'date' },
  { value: 'datetime', label: 'datetime' },
  { value: 'reference', label: 'reference' },
  { value: DOCUMENT_NUMBER_FIELD_TYPE, label: 'document number (record column)' },
];

export function EditFieldModal({ entityId, field, onClose, onUpdated, onDeleted }: Props) {
  const { tenantId, canSchemaWrite, permissions } = useAuth();
  const inactive = (field.status ?? 'ACTIVE').toUpperCase() === 'INACTIVE';
  const [name, setName] = useState(field.name);
  const [slug, setSlug] = useState(field.slug);
  const [fieldType, setFieldType] = useState(field.fieldType);
  const [formLabel, setFormLabel] = useState(() => field.labelOverride?.trim() ?? '');
  const [labelEs, setLabelEs] = useState(() => field.labels?.es?.trim() ?? '');
  const [required, setRequired] = useState(field.required);
  const [pii, setPii] = useState(field.pii);
  const [includeInListSummaryDisplay, setIncludeInListSummaryDisplay] = useState(
    () => field.includeInListSummaryDisplay === true
  );
  const [includeInSearch, setIncludeInSearch] = useState(() => configSearchable(field.config));
  const docInit = readDocGenFromConfig(field.config);
  const [docStrategy, setDocStrategy] = useState<DocumentNumberGenerationStrategy>(docInit.strategy);
  const [docPrefix, setDocPrefix] = useState(docInit.prefix);
  const [docSequenceWidth, setDocSequenceWidth] = useState(docInit.sequenceWidth);
  const [docTimeZone, setDocTimeZone] = useState(docInit.timeZone);
  const [minLenStr, setMinLenStr] = useState(() =>
    readConfigLengthString(field.config as Record<string, unknown> | null, 'minLength')
  );
  const [maxLenStr, setMaxLenStr] = useState(() =>
    readConfigLengthString(field.config as Record<string, unknown> | null, 'maxLength')
  );
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const [deleteConfirm, setDeleteConfirm] = useState(false);
  const [deletePending, setDeletePending] = useState(false);

  const [schemaLoaded, setSchemaLoaded] = useState(false);
  const [currentEntity, setCurrentEntity] = useState<EntityDto | null>(null);
  const [entities, setEntities] = useState<EntityDto[]>([]);
  const [relationships, setRelationships] = useState<EntityRelationshipDto[]>([]);

  const [targetEntitySlug, setTargetEntitySlug] = useState(() => readTargetEntitySlug(field.config));
  const [referenceLookupDisplaySlugs, setReferenceLookupDisplaySlugs] = useState(() =>
    readReferenceLookupDisplaySlugs(field.config)
  );
  const [referenceUiMode, setReferenceUiMode] = useState<'search' | 'dropdown'>(() =>
    readReferenceUiMode(field.config)
  );
  const [targetEntityFields, setTargetEntityFields] = useState<EntityFieldDto[]>([]);
  const [targetFieldsLoading, setTargetFieldsLoading] = useState(false);
  const [defineRelationship, setDefineRelationship] = useState(() => {
    const rid = readRelationshipId(field.config);
    return rid !== undefined || readTargetEntitySlug(field.config).length > 0;
  });
  const [relName, setRelName] = useState('');
  const [relSlug, setRelSlug] = useState('');
  const [relCardinality, setRelCardinality] = useState('one-to-many');
  const [relFromFieldSlug, setRelFromFieldSlug] = useState('id');

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setSchemaLoaded(false);
      try {
        const [ce, ents, rels] = await Promise.all([
          getEntity(entityId),
          listEntities(),
          listEntityRelationships(),
        ]);
        if (cancelled) return;
        setCurrentEntity(ce);
        setEntities(ents);
        setRelationships(rels);
      } catch {
        if (!cancelled) {
          setCurrentEntity(null);
          setEntities([]);
          setRelationships([]);
        }
      } finally {
        if (!cancelled) setSchemaLoaded(true);
      }
    }
    void load();
    return () => {
      cancelled = true;
    };
  }, [entityId]);

  const targetEntity = useMemo(
    () => entities.find((e) => e.slug === targetEntitySlug.trim()) ?? null,
    [entities, targetEntitySlug]
  );

  useEffect(() => {
    if (!isReferenceFieldType(fieldType) || !targetEntity?.id) {
      setTargetEntityFields([]);
      return;
    }
    let cancelled = false;
    setTargetFieldsLoading(true);
    void listFields(targetEntity.id)
      .then((flds) => {
        if (cancelled) return;
        setTargetEntityFields(flds);
        setReferenceLookupDisplaySlugs((prev) => {
          const allowed = new Set(flds.map((f) => f.slug));
          return prev.filter((s) => allowed.has(s)).slice(0, MAX_REFERENCE_LOOKUP_COLUMNS);
        });
      })
      .catch(() => {
        if (!cancelled) setTargetEntityFields([]);
      })
      .finally(() => {
        if (!cancelled) setTargetFieldsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [fieldType, targetEntity?.id]);

  const lookupColumnSelectData = useMemo(
    () =>
      activeEntityFields(targetEntityFields)
        .slice()
        .sort((a, b) => a.name.localeCompare(b.name))
        .map((f) => ({ value: f.slug, label: `${f.name} (${f.slug})` })),
    [targetEntityFields]
  );

  const matchedRelationship = useMemo(() => {
    if (!schemaLoaded) return null;
    return findRelationshipForReferenceField(relationships, {
      entityId,
      fieldSlug: slug.trim(),
      targetEntityId: targetEntity?.id ?? null,
      relationshipId: readRelationshipId(field.config),
    });
  }, [schemaLoaded, relationships, entityId, slug, targetEntity, field.config]);

  useEffect(() => {
    if (!schemaLoaded || !isReferenceFieldType(fieldType)) return;
    if (matchedRelationship) {
      setRelName(matchedRelationship.name);
      setRelSlug(matchedRelationship.slug);
      const editingFromChildSide =
        targetEntity != null &&
        matchedRelationship.fromEntityId === targetEntity.id &&
        matchedRelationship.toEntityId === entityId;
      const inv = inverseCardinalityLabel(matchedRelationship.cardinality);
      setRelCardinality(editingFromChildSide && inv ? inv : matchedRelationship.cardinality);
      setRelFromFieldSlug(matchedRelationship.fromFieldSlug?.trim() || 'id');
      return;
    }
    if (targetEntity && currentEntity) {
      setRelName(`${targetEntity.name} → ${currentEntity.name}`);
      setRelSlug(suggestedRelationshipSlug(currentEntity.slug, slug.trim(), targetEntity.slug));
      setRelCardinality('one-to-many');
      setRelFromFieldSlug('id');
    }
  }, [schemaLoaded, fieldType, matchedRelationship?.id, matchedRelationship?.cardinality, targetEntity, currentEntity, slug, entityId]);

  const targetEntitySelectData = useMemo(() => {
    return entities
      .filter((e) => e.id !== entityId)
      .sort((a, b) => a.name.localeCompare(b.name))
      .map((e) => ({ value: e.slug, label: `${e.name} (${e.slug})` }));
  }, [entities, entityId]);

  const referenceTypeSelected = isReferenceFieldType(fieldType);
  const targetsEntityStatus =
    referenceTypeSelected && targetEntitySlug.trim().toLowerCase() === ENTITY_STATUS_ENTITY_SLUG;

  const showDocumentNumberSection = isDocumentNumberFieldType(fieldType);
  const showTextLengthSection = fieldTypeSupportsTextLengthConstraints(fieldType);
  const inverseOfSelectedCardinality = inverseCardinalityLabel(relCardinality);

  const fieldTypeSelectData = useMemo(() => {
    const current = fieldType.trim();
    if (EDITABLE_FIELD_TYPES.some((o) => o.value === current)) {
      return EDITABLE_FIELD_TYPES;
    }
    return [...EDITABLE_FIELD_TYPES, { value: current, label: `${current} (custom)` }];
  }, [fieldType]);

  const mergedConfigBase = useMemo(() => {
    const base = { ...(field.config ?? {}) } as Record<string, unknown>;
    delete base.minLength;
    delete base.maxLength;
    if (includeInSearch) {
      base.isSearchable = true;
    } else {
      delete base.isSearchable;
    }
    if (showDocumentNumberSection) {
      base.documentNumberGeneration = buildDocumentNumberGenerationPayload(
        docStrategy,
        docPrefix,
        docSequenceWidth,
        docTimeZone
      );
    } else {
      delete base.documentNumberGeneration;
    }
    return base;
  }, [
    field.config,
    includeInSearch,
    showDocumentNumberSection,
    docStrategy,
    docPrefix,
    docSequenceWidth,
    docTimeZone,
  ]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (inactive) return;
    setError(null);
    setPending(true);
    try {
      const config = { ...mergedConfigBase } as Record<string, unknown>;
      if (showTextLengthSection) {
        const lenErr = applyTextLengthConstraintsToConfig(config, minLenStr, maxLenStr);
        if (lenErr) {
          setError(lenErr);
          setPending(false);
          return;
        }
      }

      const ft = fieldType.trim();
      if (isReferenceFieldType(ft)) {
        if (!targetEntitySlug.trim()) {
          setError('Reference fields require a target entity.');
          setPending(false);
          return;
        }
        config.targetEntitySlug = targetEntitySlug.trim();
        const lk = referenceLookupDisplaySlugs.slice(0, MAX_REFERENCE_LOOKUP_COLUMNS).filter(Boolean);
        if (lk.length) config.referenceLookupDisplaySlugs = lk;
        else delete config.referenceLookupDisplaySlugs;
        if (referenceUiMode === 'dropdown') config.referenceUiMode = 'dropdown';
        else delete config.referenceUiMode;
        if (!defineRelationship) {
          delete config.relationshipId;
        } else {
          const rid = readRelationshipId(field.config);
          if (rid) config.relationshipId = rid;
        }
      } else {
        delete config.targetEntitySlug;
        delete config.relationshipId;
        delete config.referenceLookupDisplaySlugs;
        delete config.referenceUiMode;
      }

      let updated = await patchField(entityId, field.id, {
        name: name.trim(),
        slug: slug.trim(),
        fieldType: ft,
        required,
        pii,
        labelOverride: formLabel.trim() || '',
        config,
        includeInListSummaryDisplay,
      });

      if (isReferenceFieldType(ft) && targetEntitySlug.trim() && defineRelationship) {
        const te = entities.find((e) => e.slug === targetEntitySlug.trim());
        if (!te) {
          setError('Target entity not found. Refresh and try again.');
          setPending(false);
          return;
        }
        const effectiveSlug = updated.slug.trim();
        const ridAfter = readRelationshipId(updated.config);
        let matched = findRelationshipForReferenceField(relationships, {
          entityId,
          fieldSlug: effectiveSlug,
          targetEntityId: te.id,
          relationshipId: ridAfter ?? readRelationshipId(field.config),
        });
        const desired = storageCardinalityAndDirection(entityId, te.id, relCardinality);
        const onStoredRow =
          Boolean(matched) &&
          matched!.fromEntityId === desired.fromEntityId &&
          matched!.toEntityId === desired.toEntityId;

        if (matched && onStoredRow) {
          const isFromSide = entityId === desired.fromEntityId;
          await patchEntityRelationship(matched.id, {
            name: relName.trim(),
            slug: relSlug.trim(),
            cardinality: desired.cardinality,
            fromFieldSlug: relFromFieldSlug.trim() || null,
            ...(isFromSide ? { toFieldSlug: effectiveSlug } : {}),
          });
          const cfg = { ...(updated.config ?? {}) } as Record<string, unknown>;
          cfg.relationshipId = matched.id;
          cfg.targetEntitySlug = te.slug;
          updated = await patchField(entityId, updated.id, { config: cfg });
        } else {
          if (matched) {
            await deleteEntityRelationship(matched.id);
          }
          const isFromSideCreate = entityId === desired.fromEntityId;
          const created = await createEntityRelationship({
            name: relName.trim(),
            slug: relSlug.trim(),
            cardinality: desired.cardinality,
            fromEntityId: desired.fromEntityId,
            toEntityId: desired.toEntityId,
            fromFieldSlug: relFromFieldSlug.trim() || null,
            toFieldSlug: isFromSideCreate ? effectiveSlug : null,
          });
          const cfg = { ...(updated.config ?? {}) } as Record<string, unknown>;
          cfg.relationshipId = created.id;
          cfg.targetEntitySlug = te.slug;
          updated = await patchField(entityId, updated.id, { config: cfg });
        }
      }

      const prevEs = field.labels?.es?.trim() ?? '';
      const nextEs = labelEs.trim();
      if (nextEs !== prevEs) {
        updated = await putFieldLabel(entityId, field.id, 'es', nextEs || null);
      }

      onUpdated(updated);
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to update field');
    } finally {
      setPending(false);
    }
  }

  async function onConfirmDelete() {
    setError(null);
    setDeletePending(true);
    try {
      const { outcome } = await deleteField(entityId, field.id);
      onDeleted?.();
      onClose();
      if (outcome === 'DEACTIVATED' && typeof window !== 'undefined') {
        window.alert(
          'This field is still referenced by stored record data, so it was deactivated (archived) instead of removed. It no longer appears on new forms or lists.'
        );
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to delete field');
    } finally {
      setDeletePending(false);
      setDeleteConfirm(false);
    }
  }

  return (
    <Modal
      title={inactive ? 'Field (inactive)' : 'Edit field'}
      onClose={onClose}
      footer={
        inactive ? (
          <Group justify="flex-end" gap="sm">
            <Button variant="default" onClick={onClose}>
              Close
            </Button>
          </Group>
        ) : deleteConfirm ? (
          <Stack gap="sm" style={{ width: '100%' }}>
            <Text size="sm">
              Delete this field? If existing records still hold a value here, the field will be{' '}
              <strong>deactivated</strong> (archived) instead of removed. Otherwise it is permanently deleted.
            </Text>
            <Group justify="flex-end" gap="sm">
              <Button variant="default" onClick={() => setDeleteConfirm(false)} disabled={deletePending}>
                Back
              </Button>
              <Button color="red" loading={deletePending} onClick={() => void onConfirmDelete()}>
                Delete
              </Button>
            </Group>
          </Stack>
        ) : (
          <Group justify="space-between" gap="sm" wrap="nowrap" style={{ width: '100%' }}>
            <Button variant="subtle" color="red" onClick={() => setDeleteConfirm(true)}>
              Delete field
            </Button>
            <Group justify="flex-end" gap="sm">
              <Button variant="default" onClick={onClose}>
                Cancel
              </Button>
              <Button type="submit" form="edit-field-form" loading={pending}>
                {pending ? 'Saving…' : 'Save'}
              </Button>
            </Group>
          </Group>
        )
      }
    >
      <form id="edit-field-form" onSubmit={(e) => void onSubmit(e)}>
        <Stack gap="md">
          {inactive ? (
            <Paper withBorder p="sm" radius="md">
              <Text size="sm">
                This field is <strong>inactive</strong>. It is kept for historical record data and cannot be edited.
                Remove it from any form layouts or list views that still reference it, then reopen the layout to
                validate.
              </Text>
            </Paper>
          ) : null}
          <fieldset disabled={inactive} style={{ border: 'none', margin: 0, padding: 0, minWidth: 0 }}>
            <Stack gap="md">
          <TextInput label="Name" value={name} onChange={(e) => setName(e.target.value)} required autoFocus />
          <TextInput
            label="Form label (optional)"
            description="Shown on forms and lists unless this placement sets its own label in Presentation. Syncs to English (en) translations."
            value={formLabel}
            onChange={(e) => setFormLabel(e.target.value)}
            placeholder={`Default: ${name.trim() || field.name}`}
          />
          <TextInput
            label="Form label — Español (es)"
            description="Optional. When the portal language is Spanish, this label is preferred."
            value={labelEs}
            onChange={(e) => setLabelEs(e.target.value)}
            placeholder={formLabel.trim() || name.trim() || field.name}
          />
          <TextInput
            label="Slug"
            description="Your API / layout key; independent of where the value is stored."
            value={slug}
            onChange={(e) => setSlug(e.target.value)}
            required
          />
          <Select
            label="Field type"
            data={fieldTypeSelectData}
            value={fieldType}
            onChange={(v) => v && setFieldType(v)}
          />
          <Checkbox label="Required" checked={required} onChange={(e) => setRequired(e.currentTarget.checked)} />
          <Checkbox label="PII" checked={pii} onChange={(e) => setPii(e.currentTarget.checked)} />
          <Checkbox
            label="Include in record list summary"
            description="Basic records table: concatenate this field with others (sort order). Server joins scalar parts in one query; reference parts resolve in the browser."
            checked={includeInListSummaryDisplay}
            onChange={(e) => setIncludeInListSummaryDisplay(e.currentTarget.checked)}
          />
          <Checkbox
            label="Include in global search / lookups"
            checked={includeInSearch}
            onChange={(e) => setIncludeInSearch(e.currentTarget.checked)}
            disabled={pii}
          />

          <Paper withBorder p="sm" radius="md">
            <Text size="sm" fw={500} mb="xs">
              Reference target & relationship
            </Text>
            {!referenceTypeSelected ? (
              <Text size="sm" c="dimmed">
                Set <strong>Field type</strong> above to <strong>reference</strong> to choose which entity this field
                points to and optionally define a record relationship (for the links API). Current type:{' '}
                <Code>{fieldType.trim() || '—'}</Code>
              </Text>
            ) : !schemaLoaded ? (
              <Text size="sm" c="dimmed">
                Loading entities and relationships…
              </Text>
            ) : (
              <Stack gap="sm">
                <Select
                  label="Target entity"
                  description="Stored in field config as targetEntitySlug."
                  data={targetEntitySelectData}
                  value={targetEntitySlug || null}
                  onChange={(v) => setTargetEntitySlug(v ?? '')}
                  searchable
                  clearable
                  placeholder="Select entity…"
                />
                {targetEntitySlug.trim() ? (
                  <>
                    <Select
                      label="Record picker"
                      description="Search opens a popup (min. 2 characters). Dropdown loads the first page of related records (up to 200) for quick selection."
                      data={[
                        { value: 'search', label: 'Search popup' },
                        { value: 'dropdown', label: 'Dropdown list' },
                      ]}
                      value={referenceUiMode}
                      onChange={(v) => v && setReferenceUiMode(v as 'search' | 'dropdown')}
                    />
                    <MultiSelect
                      label="Display columns (optional)"
                      description="Shown left-to-right in that order in search and dropdown, joined with “ - ” (max 12). Pick fields in the order you want. If none are set, the entity’s default display field is used alone."
                      data={lookupColumnSelectData}
                      value={referenceLookupDisplaySlugs}
                      onChange={(v) => setReferenceLookupDisplaySlugs(v.slice(0, MAX_REFERENCE_LOOKUP_COLUMNS))}
                      searchable
                      clearable
                      placeholder={targetFieldsLoading ? 'Loading fields…' : 'Choose fields to show beside the label…'}
                      disabled={targetFieldsLoading || lookupColumnSelectData.length === 0}
                      maxDropdownHeight={280}
                    />
                    {!targetFieldsLoading && lookupColumnSelectData.length === 0 ? (
                      <Text size="xs" c="dimmed">
                        No fields on the target entity.
                      </Text>
                    ) : null}
                    {targetsEntityStatus &&
                    tenantId &&
                    canSchemaWrite &&
                    currentEntity &&
                    canMutateEntityDefinition(permissions, currentEntity) ? (
                      <Stack gap="xs" mt="xs">
                        <Divider label="Entity status assignments" labelPosition="left" />
                        <EntityStatusAssignmentsPanel
                          tenantId={tenantId}
                          entityId={entityId}
                          scope={{ kind: 'field', fieldId: field.id }}
                          statusRefFieldPresent
                          layout="modal"
                        />
                      </Stack>
                    ) : null}
                  </>
                ) : null}
                <Checkbox
                  label="Define record relationship (for links API)"
                  description="From = this entity (parent). To = target entity. one-to-many / many-to-many show a related table on the record form (after save) instead of a single lookup."
                  checked={defineRelationship}
                  onChange={(e) => setDefineRelationship(e.currentTarget.checked)}
                  disabled={!targetEntitySlug.trim()}
                />
                {defineRelationship && targetEntitySlug.trim() ? (
                  <>
                    <TextInput
                      label="Relationship name"
                      value={relName}
                      onChange={(e) => setRelName(e.target.value)}
                      required
                    />
                    <TextInput
                      label="Relationship slug"
                      description="Unique key for record links (relationshipSlug)."
                      value={relSlug}
                      onChange={(e) => setRelSlug(e.target.value)}
                      required
                    />
                    <Select
                      label="Cardinality"
                      data={[
                        { value: 'one-to-one', label: 'one-to-one' },
                        { value: 'one-to-many', label: 'one-to-many' },
                        { value: 'many-to-one', label: 'many-to-one' },
                        { value: 'many-to-many', label: 'many-to-many' },
                      ]}
                      value={relCardinality}
                      onChange={(v) => v && setRelCardinality(v)}
                    />
                    {inverseOfSelectedCardinality ? (
                      <Text size="xs" c="dimmed">
                        The other entity sees this as <strong>{inverseOfSelectedCardinality}</strong>. Only one
                        relationship row is stored; record links always use the <strong>from</strong> entity as the parent
                        record in the links API (we align direction when you pick <strong>many-to-one</strong> here).
                      </Text>
                    ) : null}
                    <TextInput
                      label="Field on parent (from side)"
                      description="Usually id on this entity; optional metadata (e.g. DDL / tooling)."
                      value={relFromFieldSlug}
                      onChange={(e) => setRelFromFieldSlug(e.target.value)}
                    />
                    <Text size="xs" c="dimmed">
                      Stored row direction follows the dropdown: <strong>one-to-many</strong> / <strong>one-to-one</strong>{' '}
                      / <strong>many-to-many</strong> from <strong>this</strong> entity → target; <strong>many-to-one</strong>{' '}
                      is stored as <strong>one-to-many</strong> from target → this (parent on the &quot;one&quot; side is{' '}
                      <strong>from</strong> for links). Opening the target entity&apos;s reference to this one reuses the same
                      relationship when there is only one edge between the two. Related-records <strong>table</strong> on the
                      form applies to <strong>one-to-many</strong> and <strong>many-to-many</strong> on the parent field;
                      other cases use the lookup.
                    </Text>
                  </>
                ) : null}
              </Stack>
            )}
          </Paper>

          <Text size="sm" c="dimmed">
            <strong>Width on the form</strong> is set per layout: click the field on the canvas, then under{' '}
            <strong>Presentation</strong> use the <strong>Width</strong> control (full / half / third). That value is
            stored on the placement, not on the field definition.
          </Text>

          {showTextLengthSection && (
            <Paper withBorder p="sm" radius="md">
              <Text size="sm" fw={500} mb="xs">
                Text length (optional)
              </Text>
              <Text size="xs" c="dimmed" mb="sm">
                Enforced in the record form before save (and as HTML length hints). Leave blank for no limit.
              </Text>
              <Group grow align="flex-start">
                <TextInput
                  label="Min characters"
                  inputMode="numeric"
                  value={minLenStr}
                  onChange={(e) => setMinLenStr(e.target.value.replace(/\D/g, ''))}
                  placeholder="—"
                  size="sm"
                />
                <TextInput
                  label="Max characters"
                  inputMode="numeric"
                  value={maxLenStr}
                  onChange={(e) => setMaxLenStr(e.target.value.replace(/\D/g, ''))}
                  placeholder="—"
                  size="sm"
                />
              </Group>
            </Paper>
          )}

          {showDocumentNumberSection && (
            <DocumentNumberGenerationSection
              docStrategy={docStrategy}
              setDocStrategy={setDocStrategy}
              docPrefix={docPrefix}
              setDocPrefix={setDocPrefix}
              docSequenceWidth={docSequenceWidth}
              setDocSequenceWidth={setDocSequenceWidth}
              docTimeZone={docTimeZone}
              setDocTimeZone={setDocTimeZone}
            />
          )}
            </Stack>
          </fieldset>

          {error && (
            <Text role="alert" c="red" size="sm">
              {error}
            </Text>
          )}
        </Stack>
      </form>
    </Modal>
  );
}

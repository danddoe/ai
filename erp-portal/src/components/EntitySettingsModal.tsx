import { FormEvent, useEffect, useMemo, useState } from 'react';
import { Anchor, Button, Group, Radio, Select, Stack, Text, Textarea, TextInput } from '@mantine/core';
import { Link } from 'react-router-dom';
import { Modal } from './Modal';
import { EntityStatusAssignmentsPanel } from './EntityStatusAssignmentsPanel';
import { listFields, patchEntity, type DefinitionScope, type EntityDto, type EntityFieldDto } from '../api/schemas';
import { useAuth } from '../auth/AuthProvider';
import { canMutateEntityDefinition } from '../auth/jwtPermissions';
import { readReferenceFieldConfig } from '../utils/referenceFieldConfig';
import { ENTITY_STATUS_ENTITY_SLUG } from '../utils/entityStatusCatalog';

type Props = {
  entityId: string;
  entity: EntityDto;
  onClose: () => void;
  onSaved: (e: EntityDto) => void;
};

function normalizeScope(raw: EntityDto['definitionScope']): DefinitionScope {
  return raw === 'STANDARD_OBJECT' ? 'STANDARD_OBJECT' : 'TENANT_OBJECT';
}

export function EntitySettingsModal({ entityId, entity, onClose, onSaved }: Props) {
  const { canPlatformSchemaWrite, tenantId, canSchemaWrite, permissions } = useAuth();
  const [name, setName] = useState(entity.name);
  const [slug, setSlug] = useState(entity.slug);
  const [description, setDescription] = useState(entity.description ?? '');
  const [defaultSlug, setDefaultSlug] = useState(entity.defaultDisplayFieldSlug ?? '');
  const [definitionScope, setDefinitionScope] = useState<DefinitionScope>(() => normalizeScope(entity.definitionScope));
  const [fields, setFields] = useState<EntityFieldDto[]>([]);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  useEffect(() => {
    setName(entity.name);
    setSlug(entity.slug);
    setDescription(entity.description ?? '');
    setDefaultSlug(entity.defaultDisplayFieldSlug ?? '');
    setDefinitionScope(normalizeScope(entity.definitionScope));
  }, [
    entity.id,
    entity.name,
    entity.slug,
    entity.description,
    entity.defaultDisplayFieldSlug,
    entity.definitionScope,
    entity.updatedAt,
  ]);

  useEffect(() => {
    let cancelled = false;
    setLoadError(null);
    void listFields(entityId)
      .then((f) => {
        if (!cancelled) setFields([...f].sort((a, b) => a.name.localeCompare(b.name)));
      })
      .catch((e) => {
        if (!cancelled) setLoadError(e instanceof Error ? e.message : 'Failed to load fields');
      });
    return () => {
      cancelled = true;
    };
  }, [entityId]);

  const canEditEntityDefinition = useMemo(
    () => canMutateEntityDefinition(permissions, entity),
    [permissions, entity]
  );

  const statusRefFieldPresent = useMemo(
    () =>
      fields.some(
        (f) =>
          f.fieldType?.toLowerCase() === 'reference' &&
          readReferenceFieldConfig(f.config).targetEntitySlug === ENTITY_STATUS_ENTITY_SLUG
      ),
    [fields]
  );

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setPending(true);
    try {
      const body: Parameters<typeof patchEntity>[1] = {
        name: name.trim(),
        slug: slug.trim(),
        description: description.trim() || null,
      };
      if (defaultSlug === '') {
        body.clearDefaultDisplayField = true;
      } else {
        body.defaultDisplayFieldSlug = defaultSlug;
      }
      body.definitionScope = definitionScope;
      const dto = await patchEntity(entityId, body);
      onSaved(dto);
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Save failed');
    } finally {
      setPending(false);
    }
  }

  return (
    <Modal
      title="Entity settings"
      onClose={onClose}
      footer={
        <Group justify="flex-end" gap="sm">
          <Button variant="default" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" form="entity-settings-form" loading={pending}>
            {pending ? 'Saving…' : 'Save'}
          </Button>
        </Group>
      }
    >
      <form id="entity-settings-form" onSubmit={(e) => void onSubmit(e)}>
        <Stack gap="md">
          {loadError && (
            <Text role="status" c="red" size="sm">
              {loadError}
            </Text>
          )}
          <TextInput label="Name" value={name} onChange={(e) => setName(e.target.value)} required />
          <TextInput label="Slug" value={slug} onChange={(e) => setSlug(e.target.value)} required />
          <Textarea label="Description" rows={2} value={description} onChange={(e) => setDescription(e.target.value)} />

          {canPlatformSchemaWrite ? (
            <Radio.Group
              label="Entity scope"
              value={definitionScope}
              onChange={(v) => setDefinitionScope(v as DefinitionScope)}
            >
              <Stack gap="xs" mt="xs">
                <Radio
                  value="TENANT_OBJECT"
                  label={
                    <Stack gap={2}>
                      <Text size="sm" fw={600}>
                        Tenant entity
                      </Text>
                      <Text size="xs" c="dimmed">
                        Standard tenant-owned definition; editable with tenant schema write.
                      </Text>
                    </Stack>
                  }
                />
                <Radio
                  value="STANDARD_OBJECT"
                  label={
                    <Stack gap={2}>
                      <Text size="sm" fw={600}>
                        Core (catalog) entity
                      </Text>
                      <Text size="xs" c="dimmed">
                        Platform catalog scope; definition changes require full platform schema write.
                      </Text>
                    </Stack>
                  }
                />
              </Stack>
            </Radio.Group>
          ) : (
            <div>
              <Text size="sm" fw={500} mb={4}>
                Entity scope
              </Text>
              <Text size="sm" c="dimmed">
                {entity.definitionScope === 'STANDARD_OBJECT'
                  ? 'Core (catalog) entity — editing this definition requires full platform schema permissions.'
                  : 'Tenant entity'}
              </Text>
            </div>
          )}

          <Select
            label="Default display field"
            description="Lookups / labels"
            data={[
              { value: '', label: '— None —' },
              ...fields.map((f) => ({ value: f.slug, label: `${f.name} (${f.slug})` })),
            ]}
            value={defaultSlug}
            onChange={(v) => setDefaultSlug(v ?? '')}
            searchable
            clearable
          />

          {tenantId && canSchemaWrite && canEditEntityDefinition && (
            <Stack gap="xs">
              <Anchor component={Link} to={`/entities/${entityId}/layouts#status-assignments`} size="sm">
                Open status assignments on Form layouts page
              </Anchor>
              <EntityStatusAssignmentsPanel
                tenantId={tenantId}
                entityId={entityId}
                scope={{ kind: 'entity' }}
                statusRefFieldPresent={statusRefFieldPresent}
                layout="modal"
              />
            </Stack>
          )}

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

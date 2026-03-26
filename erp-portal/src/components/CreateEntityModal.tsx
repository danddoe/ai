import { FormEvent, useState } from 'react';
import { Button, Group, Radio, Stack, Text, Textarea, TextInput } from '@mantine/core';
import { Modal } from './Modal';
import { createEntity, type DefinitionScope, type EntityDto } from '../api/schemas';
import { useAuth } from '../auth/AuthProvider';

type Props = {
  onClose: () => void;
  onCreated: (e: EntityDto) => void;
};

export function CreateEntityModal({ onClose, onCreated }: Props) {
  const { canPlatformSchemaWrite } = useAuth();
  const [name, setName] = useState('');
  const [slug, setSlug] = useState('');
  const [description, setDescription] = useState('');
  const [definitionScope, setDefinitionScope] = useState<DefinitionScope>('TENANT_OBJECT');
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setPending(true);
    try {
      const dto = await createEntity({
        name: name.trim(),
        slug: slug.trim(),
        description: description.trim() || undefined,
        ...(canPlatformSchemaWrite ? { definitionScope } : {}),
      });
      onCreated(dto);
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create entity');
    } finally {
      setPending(false);
    }
  }

  return (
    <Modal
      title="New entity"
      onClose={onClose}
      footer={
        <Group justify="flex-end" gap="sm">
          <Button variant="default" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" form="create-entity-form" loading={pending}>
            {pending ? 'Creating…' : 'Create'}
          </Button>
        </Group>
      }
    >
      <form id="create-entity-form" onSubmit={(e) => void onSubmit(e)}>
        <Stack gap="md">
          <TextInput label="Name" value={name} onChange={(e) => setName(e.target.value)} required autoFocus />
          <TextInput
            label="Slug"
            value={slug}
            onChange={(e) => setSlug(e.target.value)}
            required
            description={
              <Text size="xs" c="dimmed" component="span">
                Unique per tenant, e.g. <code>sales_order</code>
              </Text>
            }
          />
          <Textarea
            label="Description"
            description="Optional"
            rows={2}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
          />

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
                        Default for normal tenant work; editable with tenant schema write.
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
                        Platform catalog; definition changes require full platform schema write.
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
                New entities are created as <strong>tenant entities</strong>.
              </Text>
            </div>
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

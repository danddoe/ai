import { FormEvent, useEffect, useState } from 'react';
import { Button, Checkbox, Group, Select, Stack, Text, TextInput } from '@mantine/core';
import { Modal } from '../Modal';
import { createLayoutFromTemplate, listFormLayoutTemplates, type FormLayoutTemplateDto } from '../../api/schemas';

type Props = {
  entityId: string;
  onClose: () => void;
  onCreated: (layoutId: string) => void;
};

export function TemplatePickerModal({ entityId, onClose, onCreated }: Props) {
  const [templates, setTemplates] = useState<FormLayoutTemplateDto[] | null>(null);
  const [loadErr, setLoadErr] = useState<string | null>(null);
  const [templateKey, setTemplateKey] = useState('');
  const [name, setName] = useState('');
  const [isDefault, setIsDefault] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      try {
        const list = await listFormLayoutTemplates(false);
        if (!cancelled) {
          setTemplates(list);
          if (list.length > 0) setTemplateKey(list[0].templateKey);
        }
      } catch (e) {
        if (!cancelled) setLoadErr(e instanceof Error ? e.message : 'Failed to load templates');
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setPending(true);
    try {
      const dto = await createLayoutFromTemplate(entityId, {
        templateKey,
        name: name.trim(),
        isDefault,
      });
      onCreated(dto.id);
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create layout');
    } finally {
      setPending(false);
    }
  }

  const selectedTpl = templates?.find((t) => t.templateKey === templateKey);

  return (
    <Modal
      title="Start from template"
      onClose={onClose}
      footer={
        <Group justify="flex-end" gap="sm">
          <Button variant="default" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" form="tpl-form" loading={pending} disabled={!templates?.length}>
            {pending ? 'Creating…' : 'Create layout'}
          </Button>
        </Group>
      }
    >
      {loadErr && (
        <Text role="alert" c="red" size="sm" mb="md">
          {loadErr}
        </Text>
      )}
      {templates && (
        <form id="tpl-form" onSubmit={(e) => void onSubmit(e)}>
          <Stack gap="md">
            <Select
              label="Template"
              data={templates.map((t) => ({
                value: t.templateKey,
                label: `${t.title} (${t.templateKey})`,
              }))}
              value={templateKey}
              onChange={(v) => v && setTemplateKey(v)}
              required
            />
            {selectedTpl?.description && (
              <Text size="sm" c="dimmed">
                {selectedTpl.description}
              </Text>
            )}
            <TextInput label="Layout name" value={name} onChange={(e) => setName(e.target.value)} required />
            <Checkbox
              label="Set as default layout"
              checked={isDefault}
              onChange={(e) => setIsDefault(e.currentTarget.checked)}
            />
            {error && (
              <Text role="alert" c="red" size="sm">
                {error}
              </Text>
            )}
          </Stack>
        </form>
      )}
    </Modal>
  );
}

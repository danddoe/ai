import { FormEvent, useMemo, useState } from 'react';
import { Button, Checkbox, Group, Paper, Select, Stack, Text, TextInput } from '@mantine/core';
import { Modal } from '../Modal';
import {
  DOCUMENT_NUMBER_FIELD_TYPE,
  createField,
  isDocumentNumberFieldType,
  type DocumentNumberGenerationStrategy,
  type EntityFieldDto,
} from '../../api/schemas';
import { DocumentNumberGenerationSection } from './DocumentNumberGenerationSection';
import { buildDocumentNumberGenerationPayload } from './documentNumberGeneration';
import {
  applyTextLengthConstraintsToConfig,
  fieldTypeSupportsTextLengthConstraints,
} from '../../utils/fieldTextConstraints';

const FIELD_TYPE_DATA = [
  { value: 'string', label: 'string' },
  { value: 'text', label: 'text' },
  { value: 'number', label: 'number' },
  { value: 'boolean', label: 'boolean' },
  { value: 'date', label: 'date' },
  { value: 'datetime', label: 'datetime' },
  { value: 'reference', label: 'reference' },
  { value: DOCUMENT_NUMBER_FIELD_TYPE, label: 'document number (record column)' },
];

type Props = {
  entityId: string;
  suggestedName?: string;
  suggestedSlug?: string;
  onClose: () => void;
  onCreated: (f: EntityFieldDto) => void;
};

export function CreateFieldModal({
  entityId,
  suggestedName = '',
  suggestedSlug = '',
  onClose,
  onCreated,
}: Props) {
  const [name, setName] = useState(suggestedName);
  const [slug, setSlug] = useState(suggestedSlug);
  const [fieldType, setFieldType] = useState('string');
  const [required, setRequired] = useState(false);
  const [pii, setPii] = useState(false);
  const [includeInSearch, setIncludeInSearch] = useState(false);
  const [docStrategy, setDocStrategy] = useState<DocumentNumberGenerationStrategy>('MANUAL');
  const [docPrefix, setDocPrefix] = useState('');
  const [docSequenceWidth, setDocSequenceWidth] = useState(4);
  const [docTimeZone, setDocTimeZone] = useState('UTC');
  const [minLenStr, setMinLenStr] = useState('');
  const [maxLenStr, setMaxLenStr] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  const showDocumentNumberSection = isDocumentNumberFieldType(fieldType);
  const showTextLengthSection = fieldTypeSupportsTextLengthConstraints(fieldType);

  const mergedConfigBase = useMemo(() => {
    const base: Record<string, unknown> = {};
    if (includeInSearch) {
      base.isSearchable = true;
    }
    if (showDocumentNumberSection) {
      base.documentNumberGeneration = buildDocumentNumberGenerationPayload(
        docStrategy,
        docPrefix,
        docSequenceWidth,
        docTimeZone
      );
    }
    return base;
  }, [
    includeInSearch,
    showDocumentNumberSection,
    docStrategy,
    docPrefix,
    docSequenceWidth,
    docTimeZone,
  ]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setPending(true);
    try {
      const config: Record<string, unknown> = { ...mergedConfigBase };
      if (showTextLengthSection) {
        const lenErr = applyTextLengthConstraintsToConfig(config, minLenStr, maxLenStr);
        if (lenErr) {
          setError(lenErr);
          setPending(false);
          return;
        }
      }
      const f = await createField(entityId, {
        name: name.trim(),
        slug: slug.trim(),
        fieldType,
        required,
        pii,
        config: Object.keys(config).length > 0 ? config : undefined,
      });
      onCreated(f);
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create field');
    } finally {
      setPending(false);
    }
  }

  return (
    <Modal
      title="New database field"
      onClose={onClose}
      footer={
        <Group justify="flex-end" gap="sm">
          <Button variant="default" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" form="create-field-form" loading={pending}>
            {pending ? 'Creating…' : 'Create'}
          </Button>
        </Group>
      }
    >
      <form id="create-field-form" onSubmit={(e) => void onSubmit(e)}>
        <Stack gap="md">
          <TextInput label="Name" value={name} onChange={(e) => setName(e.target.value)} required autoFocus />
          <TextInput label="Slug" value={slug} onChange={(e) => setSlug(e.target.value)} required />
          <Select
            label="Field type"
            data={FIELD_TYPE_DATA}
            value={fieldType}
            onChange={(v) => v && setFieldType(v)}
          />
          <Checkbox label="Required" checked={required} onChange={(e) => setRequired(e.currentTarget.checked)} />
          <Checkbox label="PII" checked={pii} onChange={(e) => setPii(e.currentTarget.checked)} />
          <Checkbox
            label="Include in global search / lookups"
            checked={includeInSearch}
            onChange={(e) => setIncludeInSearch(e.currentTarget.checked)}
            disabled={pii}
          />

          <Text size="sm" c="dimmed">
            <strong>Width on the form</strong> is set after you add the field: select it on the layout →{' '}
            <strong>Presentation</strong> → <strong>Width</strong> (full / half / third).
          </Text>

          {showTextLengthSection && (
            <Paper withBorder p="sm" radius="md">
              <Text size="sm" fw={500} mb="xs">
                Text length (optional)
              </Text>
              <Text size="xs" c="dimmed" mb="sm">
                Enforced in the record form before save. Leave blank for no limit.
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

import { FormEvent, useMemo, useState } from 'react';
import { Modal } from '../Modal';
import {
  DOCUMENT_NUMBER_FIELD_TYPE,
  isDocumentNumberFieldType,
  patchField,
  type DocumentNumberGenerationStrategy,
  type EntityFieldDto,
} from '../../api/schemas';
import { DocumentNumberGenerationSection } from './DocumentNumberGenerationSection';
import {
  buildDocumentNumberGenerationPayload,
  readDocGenFromConfig,
} from './documentNumberGeneration';

type Props = {
  entityId: string;
  field: EntityFieldDto;
  onClose: () => void;
  onUpdated: (f: EntityFieldDto) => void;
};

function configSearchable(config: EntityFieldDto['config']): boolean {
  return config?.isSearchable === true;
}

const EDITABLE_FIELD_TYPES: { value: string; label: string }[] = [
  { value: 'text', label: 'text' },
  { value: 'number', label: 'number' },
  { value: 'boolean', label: 'boolean' },
  { value: 'date', label: 'date' },
  { value: 'datetime', label: 'datetime' },
  { value: 'reference', label: 'reference' },
  { value: DOCUMENT_NUMBER_FIELD_TYPE, label: 'document number (record column)' },
];

export function EditFieldModal({ entityId, field, onClose, onUpdated }: Props) {
  const [name, setName] = useState(field.name);
  const [slug, setSlug] = useState(field.slug);
  const [fieldType, setFieldType] = useState(field.fieldType);
  const [formLabel, setFormLabel] = useState(() => field.labelOverride?.trim() ?? '');
  const [required, setRequired] = useState(field.required);
  const [pii, setPii] = useState(field.pii);
  const [includeInSearch, setIncludeInSearch] = useState(() => configSearchable(field.config));
  const docInit = readDocGenFromConfig(field.config);
  const [docStrategy, setDocStrategy] = useState<DocumentNumberGenerationStrategy>(docInit.strategy);
  const [docPrefix, setDocPrefix] = useState(docInit.prefix);
  const [docSequenceWidth, setDocSequenceWidth] = useState(docInit.sequenceWidth);
  const [docTimeZone, setDocTimeZone] = useState(docInit.timeZone);
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  const showDocumentNumberSection = isDocumentNumberFieldType(fieldType);

  const fieldTypeSelectOptions = useMemo(() => {
    const current = fieldType.trim();
    if (EDITABLE_FIELD_TYPES.some((o) => o.value === current)) {
      return EDITABLE_FIELD_TYPES;
    }
    return [...EDITABLE_FIELD_TYPES, { value: current, label: `${current} (custom)` }];
  }, [fieldType]);

  const mergedConfig = useMemo(() => {
    const base = { ...(field.config ?? {}) } as Record<string, unknown>;
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
    setError(null);
    setPending(true);
    try {
      const updated = await patchField(entityId, field.id, {
        name: name.trim(),
        slug: slug.trim(),
        fieldType: fieldType.trim(),
        required,
        pii,
        labelOverride: formLabel.trim() || '',
        config: mergedConfig,
      });
      onUpdated(updated);
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to update field');
    } finally {
      setPending(false);
    }
  }

  return (
    <Modal
      title="Edit field"
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" form="edit-field-form" className="btn btn-primary" disabled={pending}>
            {pending ? 'Saving…' : 'Save'}
          </button>
        </>
      }
    >
      <form id="edit-field-form" onSubmit={(e) => void onSubmit(e)} style={{ display: 'grid', gap: 12 }}>
        <label className="field-label">
          Name
          <input className="input" value={name} onChange={(e) => setName(e.target.value)} required autoFocus />
        </label>
        <label className="field-label">
          Form label (optional)
          <input
            className="input"
            value={formLabel}
            onChange={(e) => setFormLabel(e.target.value)}
            placeholder={`Default: ${name.trim() || field.name}`}
          />
          <span className="builder-muted" style={{ fontSize: '0.75rem', display: 'block', marginTop: 4 }}>
            Shown on forms and lists unless this placement sets its own label in Presentation.
          </span>
        </label>
        <label className="field-label">
          Slug
          <input className="input" value={slug} onChange={(e) => setSlug(e.target.value)} required />
          <span className="builder-muted" style={{ fontSize: '0.75rem', display: 'block', marginTop: 4 }}>
            Your API / layout key; independent of where the value is stored.
          </span>
        </label>
        <label className="field-label">
          Field type
          <select className="input" value={fieldType} onChange={(e) => setFieldType(e.target.value)}>
            {fieldTypeSelectOptions.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
        </label>
        <label className="field-label row">
          <input type="checkbox" checked={required} onChange={(e) => setRequired(e.target.checked)} />
          Required
        </label>
        <label className="field-label row">
          <input type="checkbox" checked={pii} onChange={(e) => setPii(e.target.checked)} />
          PII
        </label>
        <label className="field-label row">
          <input
            type="checkbox"
            checked={includeInSearch}
            onChange={(e) => setIncludeInSearch(e.target.checked)}
            disabled={pii}
          />
          Include in global search / lookups
        </label>

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
          <p role="alert" className="text-error">
            {error}
          </p>
        )}
      </form>
    </Modal>
  );
}

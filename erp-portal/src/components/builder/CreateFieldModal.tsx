import { FormEvent, useMemo, useState } from 'react';
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

type Props = {
  entityId: string;
  /** Prefill when opened (e.g. from an unresolved layout placement). */
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
  const [fieldType, setFieldType] = useState('text');
  const [required, setRequired] = useState(false);
  const [pii, setPii] = useState(false);
  const [includeInSearch, setIncludeInSearch] = useState(false);
  const [docStrategy, setDocStrategy] = useState<DocumentNumberGenerationStrategy>('MANUAL');
  const [docPrefix, setDocPrefix] = useState('');
  const [docSequenceWidth, setDocSequenceWidth] = useState(4);
  const [docTimeZone, setDocTimeZone] = useState('UTC');
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  const showDocumentNumberSection = isDocumentNumberFieldType(fieldType);

  const mergedConfig = useMemo(() => {
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
    return Object.keys(base).length > 0 ? base : undefined;
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
      const f = await createField(entityId, {
        name: name.trim(),
        slug: slug.trim(),
        fieldType,
        required,
        pii,
        config: mergedConfig,
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
        <>
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" form="create-field-form" className="btn btn-primary" disabled={pending}>
            {pending ? 'Creating…' : 'Create'}
          </button>
        </>
      }
    >
      <form id="create-field-form" onSubmit={(e) => void onSubmit(e)} style={{ display: 'grid', gap: 12 }}>
        <label className="field-label">
          Name
          <input
            className="input"
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
            autoFocus
          />
        </label>
        <label className="field-label">
          Slug
          <input className="input" value={slug} onChange={(e) => setSlug(e.target.value)} required />
        </label>
        <label className="field-label">
          Field type
          <select className="input" value={fieldType} onChange={(e) => setFieldType(e.target.value)}>
            <option value="text">text</option>
            <option value="number">number</option>
            <option value="boolean">boolean</option>
            <option value="date">date</option>
            <option value="datetime">datetime</option>
            <option value="reference">reference</option>
            <option value={DOCUMENT_NUMBER_FIELD_TYPE}>document number (record column)</option>
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

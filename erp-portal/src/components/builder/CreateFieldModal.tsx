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
import {
  applyTextLengthConstraintsToConfig,
  fieldTypeSupportsTextLengthConstraints,
} from '../../utils/fieldTextConstraints';

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
            <option value="string">string</option>
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

        <p className="builder-muted" style={{ fontSize: '0.8125rem', margin: 0 }}>
          <strong>Width on the form</strong> is set after you add the field: select it on the layout →{' '}
          <strong>Presentation</strong> → <strong>Width</strong> (full / half / third).
        </p>

        {showTextLengthSection && (
          <fieldset style={{ border: '1px solid #e4e4e7', borderRadius: 8, padding: '10px 12px', margin: 0 }}>
            <legend style={{ fontSize: '0.8125rem', padding: '0 6px' }}>Text length (optional)</legend>
            <p className="builder-muted" style={{ fontSize: '0.75rem', margin: '0 0 8px' }}>
              Enforced in the record form before save. Leave blank for no limit.
            </p>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
              <label className="field-label" style={{ margin: 0 }}>
                Min characters
                <input
                  className="input"
                  inputMode="numeric"
                  value={minLenStr}
                  onChange={(e) => setMinLenStr(e.target.value.replace(/\D/g, ''))}
                  placeholder="—"
                />
              </label>
              <label className="field-label" style={{ margin: 0 }}>
                Max characters
                <input
                  className="input"
                  inputMode="numeric"
                  value={maxLenStr}
                  onChange={(e) => setMaxLenStr(e.target.value.replace(/\D/g, ''))}
                  placeholder="—"
                />
              </label>
            </div>
          </fieldset>
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
          <p role="alert" className="text-error">
            {error}
          </p>
        )}
      </form>
    </Modal>
  );
}

import { FormEvent, useEffect, useState } from 'react';
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

  return (
    <Modal
      title="Start from template"
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" form="tpl-form" className="btn btn-primary" disabled={pending || !templates?.length}>
            {pending ? 'Creating…' : 'Create layout'}
          </button>
        </>
      }
    >
      {loadErr && (
        <p role="alert" className="text-error">
          {loadErr}
        </p>
      )}
      {templates && (
        <form id="tpl-form" onSubmit={(e) => void onSubmit(e)} style={{ display: 'grid', gap: 12 }}>
          <label className="field-label">
            Template
            <select
              className="input"
              value={templateKey}
              onChange={(e) => setTemplateKey(e.target.value)}
              required
            >
              {templates.map((t) => (
                <option key={t.templateKey} value={t.templateKey}>
                  {t.title} ({t.templateKey})
                </option>
              ))}
            </select>
          </label>
          {templates.find((t) => t.templateKey === templateKey)?.description && (
            <p style={{ margin: 0, fontSize: '0.8125rem', color: '#71717a' }}>
              {templates.find((t) => t.templateKey === templateKey)?.description}
            </p>
          )}
          <label className="field-label">
            Layout name
            <input className="input" value={name} onChange={(e) => setName(e.target.value)} required />
          </label>
          <label className="field-label row">
            <input type="checkbox" checked={isDefault} onChange={(e) => setIsDefault(e.target.checked)} />
            Set as default layout
          </label>
          {error && (
            <p role="alert" className="text-error">
              {error}
            </p>
          )}
        </form>
      )}
    </Modal>
  );
}

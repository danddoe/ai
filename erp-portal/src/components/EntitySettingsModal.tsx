import { FormEvent, useEffect, useState } from 'react';
import { Modal } from './Modal';
import { listFields, patchEntity, type EntityDto, type EntityFieldDto } from '../api/schemas';

type Props = {
  entityId: string;
  entity: EntityDto;
  onClose: () => void;
  onSaved: (e: EntityDto) => void;
};

export function EntitySettingsModal({ entityId, entity, onClose, onSaved }: Props) {
  const [name, setName] = useState(entity.name);
  const [slug, setSlug] = useState(entity.slug);
  const [description, setDescription] = useState(entity.description ?? '');
  const [defaultSlug, setDefaultSlug] = useState(entity.defaultDisplayFieldSlug ?? '');
  const [fields, setFields] = useState<EntityFieldDto[]>([]);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

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
        <>
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" form="entity-settings-form" className="btn btn-primary" disabled={pending}>
            {pending ? 'Saving…' : 'Save'}
          </button>
        </>
      }
    >
      <form id="entity-settings-form" onSubmit={(e) => void onSubmit(e)} style={{ display: 'grid', gap: 12 }}>
        {loadError && (
          <p role="status" className="text-error">
            {loadError}
          </p>
        )}
        <label className="field-label">
          Name
          <input className="input" value={name} onChange={(e) => setName(e.target.value)} required />
        </label>
        <label className="field-label">
          Slug
          <input className="input" value={slug} onChange={(e) => setSlug(e.target.value)} required />
        </label>
        <label className="field-label">
          Description
          <textarea className="input" rows={2} value={description} onChange={(e) => setDescription(e.target.value)} />
        </label>
        <label className="field-label">
          Default display field <span className="builder-muted">(lookups / labels)</span>
          <select className="input" value={defaultSlug} onChange={(e) => setDefaultSlug(e.target.value)}>
            <option value="">— None —</option>
            {fields.map((f) => (
              <option key={f.id} value={f.slug}>
                {f.name} ({f.slug})
              </option>
            ))}
          </select>
        </label>
        {error && (
          <p role="alert" className="text-error">
            {error}
          </p>
        )}
      </form>
    </Modal>
  );
}

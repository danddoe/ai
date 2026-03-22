import { FormEvent, useState } from 'react';
import { Modal } from './Modal';
import { createEntity, type EntityDto } from '../api/schemas';

type Props = {
  onClose: () => void;
  onCreated: (e: EntityDto) => void;
};

export function CreateEntityModal({ onClose, onCreated }: Props) {
  const [name, setName] = useState('');
  const [slug, setSlug] = useState('');
  const [description, setDescription] = useState('');
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
        <>
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" form="create-entity-form" className="btn btn-primary" disabled={pending}>
            {pending ? 'Creating…' : 'Create'}
          </button>
        </>
      }
    >
      <form id="create-entity-form" onSubmit={(e) => void onSubmit(e)} style={{ display: 'grid', gap: 12 }}>
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
          <span className="builder-muted" style={{ fontSize: '0.75rem', fontWeight: 400 }}>
            Unique per tenant, e.g. <code>sales_order</code>
          </span>
        </label>
        <label className="field-label">
          Description <span className="builder-muted">(optional)</span>
          <textarea
            className="input"
            rows={2}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
          />
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

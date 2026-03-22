import { useCallback } from 'react';
import { JsonDocumentView, stringifyPayload } from './JsonDocumentView';
import { Modal } from './Modal';

type Props = {
  payload: unknown;
  title?: string;
  onClose: () => void;
};

export function AuditPayloadModal({ payload, title = 'Audit payload', onClose }: Props) {
  const copy = useCallback(async () => {
    const raw = stringifyPayload(payload);
    try {
      await navigator.clipboard.writeText(raw);
    } catch {
      // ignore
    }
  }, [payload]);

  return (
    <Modal
      title={title}
      onClose={onClose}
      extraWide
      footer={
        <>
          <button type="button" className="btn btn-secondary btn-sm" onClick={() => void copy()}>
            Copy JSON
          </button>
          <button type="button" className="btn btn-primary btn-sm" onClick={onClose}>
            Close
          </button>
        </>
      }
    >
      <p className="json-doc-lead builder-muted">
        Formatted like a readable document; use <strong>Copy JSON</strong> for the raw text.
      </p>
      <JsonDocumentView value={payload} label="Formatted audit payload" />
    </Modal>
  );
}

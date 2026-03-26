import { useCallback } from 'react';
import { Button, Group, Text } from '@mantine/core';
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
        <Group justify="flex-end" gap="sm">
          <Button variant="default" size="xs" onClick={() => void copy()}>
            Copy JSON
          </Button>
          <Button size="xs" onClick={onClose}>
            Close
          </Button>
        </Group>
      }
    >
      <Text size="sm" c="dimmed" className="json-doc-lead">
        Formatted like a readable document; use <strong>Copy JSON</strong> for the raw text.
      </Text>
      <JsonDocumentView value={payload} label="Formatted audit payload" />
    </Modal>
  );
}

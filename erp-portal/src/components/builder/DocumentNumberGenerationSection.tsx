import { NumberInput, Select, Stack, Text, TextInput } from '@mantine/core';
import type { DocumentNumberGenerationStrategy } from '../../api/schemas';
import { STRATEGY_HELP, STRATEGY_LABELS } from './documentNumberGeneration';

type Props = {
  docStrategy: DocumentNumberGenerationStrategy;
  setDocStrategy: (s: DocumentNumberGenerationStrategy) => void;
  docPrefix: string;
  setDocPrefix: (s: string) => void;
  docSequenceWidth: number;
  setDocSequenceWidth: (n: number) => void;
  docTimeZone: string;
  setDocTimeZone: (s: string) => void;
};

const STRATEGY_OPTIONS = (Object.keys(STRATEGY_LABELS) as DocumentNumberGenerationStrategy[]).map((k) => ({
  value: k,
  label: STRATEGY_LABELS[k],
}));

export function DocumentNumberGenerationSection({
  docStrategy,
  setDocStrategy,
  docPrefix,
  setDocPrefix,
  docSequenceWidth,
  setDocSequenceWidth,
  docTimeZone,
  setDocTimeZone,
}: Props) {
  return (
    <fieldset
      style={{
        margin: 0,
        padding: '12px 14px',
        border: '1px solid var(--mantine-color-default-border)',
        borderRadius: 'var(--mantine-radius-md)',
      }}
    >
      <legend style={{ padding: '0 6px', fontWeight: 600, fontSize: '0.875rem' }}>Document number generation</legend>
      <Stack gap="sm" mt="xs">
        <Text size="xs" c="dimmed">
          This field type stores values on the record as <code>businessDocumentNumber</code> (string). The strategy
          below is saved in <code>config.documentNumberGeneration</code> for the API to apply on create.
        </Text>
        <Select
          label="Strategy"
          data={STRATEGY_OPTIONS}
          value={docStrategy}
          onChange={(v) => v && setDocStrategy(v as DocumentNumberGenerationStrategy)}
          size="sm"
        />
        <Text size="xs" c="dimmed">
          {STRATEGY_HELP[docStrategy]}
        </Text>
        {(docStrategy === 'TIMESTAMP' ||
          docStrategy === 'TENANT_SEQUENCE' ||
          docStrategy === 'MONTHLY_SEQUENCE') && (
          <TextInput
            label="Prefix (optional)"
            description="Placed before the generated part (e.g. JV + 2026050001)."
            value={docPrefix}
            onChange={(e) => setDocPrefix(e.target.value)}
            placeholder="e.g. JV"
            maxLength={32}
            size="sm"
          />
        )}
        {(docStrategy === 'TENANT_SEQUENCE' || docStrategy === 'MONTHLY_SEQUENCE') && (
          <NumberInput
            label="Sequence width"
            description="Zero-padded length of the numeric suffix (e.g. width 4 → 0001)."
            min={1}
            max={12}
            value={docSequenceWidth}
            onChange={(v) => setDocSequenceWidth(typeof v === 'number' ? v : 4)}
            size="sm"
          />
        )}
        {docStrategy === 'MONTHLY_SEQUENCE' && (
          <TextInput
            label="Time zone"
            description="IANA id (e.g. America/New_York) used to decide which month a new number belongs to."
            value={docTimeZone}
            onChange={(e) => setDocTimeZone(e.target.value)}
            placeholder="UTC"
            maxLength={64}
            size="sm"
          />
        )}
      </Stack>
    </fieldset>
  );
}

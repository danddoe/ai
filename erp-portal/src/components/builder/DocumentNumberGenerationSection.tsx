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
        border: '1px solid var(--border-subtle, #ddd)',
        borderRadius: 8,
        display: 'grid',
        gap: 12,
      }}
    >
      <legend style={{ padding: '0 6px', fontWeight: 600 }}>Document number generation</legend>
      <p className="builder-muted" style={{ fontSize: '0.8rem', margin: 0 }}>
        This field type stores values on the record as <code>businessDocumentNumber</code> (string). The strategy
        below is saved in <code>config.documentNumberGeneration</code> for the API to apply on create.
      </p>
      <label className="field-label">
        Strategy
        <select
          className="input"
          value={docStrategy}
          onChange={(e) => setDocStrategy(e.target.value as DocumentNumberGenerationStrategy)}
        >
          {(Object.keys(STRATEGY_LABELS) as DocumentNumberGenerationStrategy[]).map((k) => (
            <option key={k} value={k}>
              {STRATEGY_LABELS[k]}
            </option>
          ))}
        </select>
      </label>
      <p className="builder-muted" style={{ fontSize: '0.78rem', margin: 0 }}>
        {STRATEGY_HELP[docStrategy]}
      </p>
      {(docStrategy === 'TIMESTAMP' ||
        docStrategy === 'TENANT_SEQUENCE' ||
        docStrategy === 'MONTHLY_SEQUENCE') && (
        <label className="field-label">
          Prefix (optional)
          <input
            className="input"
            value={docPrefix}
            onChange={(e) => setDocPrefix(e.target.value)}
            placeholder="e.g. JV"
            maxLength={32}
          />
          <span className="builder-muted" style={{ fontSize: '0.75rem', display: 'block', marginTop: 4 }}>
            Placed before the generated part (e.g. <code>JV</code> + <code>2026050001</code>).
          </span>
        </label>
      )}
      {(docStrategy === 'TENANT_SEQUENCE' || docStrategy === 'MONTHLY_SEQUENCE') && (
        <label className="field-label">
          Sequence width
          <input
            className="input"
            type="number"
            min={1}
            max={12}
            value={docSequenceWidth}
            onChange={(e) => setDocSequenceWidth(Number(e.target.value) || 4)}
          />
          <span className="builder-muted" style={{ fontSize: '0.75rem', display: 'block', marginTop: 4 }}>
            Zero-padded length of the numeric suffix (e.g. width 4 → 0001).
          </span>
        </label>
      )}
      {docStrategy === 'MONTHLY_SEQUENCE' && (
        <label className="field-label">
          Time zone
          <input
            className="input"
            value={docTimeZone}
            onChange={(e) => setDocTimeZone(e.target.value)}
            placeholder="UTC"
            maxLength={64}
          />
          <span className="builder-muted" style={{ fontSize: '0.75rem', display: 'block', marginTop: 4 }}>
            IANA id (e.g. <code>America/New_York</code>) used to decide which month a new number belongs to.
          </span>
        </label>
      )}
    </fieldset>
  );
}

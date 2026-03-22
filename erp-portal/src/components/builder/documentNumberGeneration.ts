import type {
  DocumentNumberGenerationConfig,
  DocumentNumberGenerationStrategy,
  EntityFieldDto,
} from '../../api/schemas';

export const STRATEGY_LABELS: Record<DocumentNumberGenerationStrategy, string> = {
  MANUAL: 'Manual / API only',
  TIMESTAMP: 'Timestamp-based',
  TENANT_SEQUENCE: 'Sequential (per tenant & entity)',
  MONTHLY_SEQUENCE: 'Monthly reset (prefix + YYYYMM + sequence)',
};

export const STRATEGY_HELP: Record<DocumentNumberGenerationStrategy, string> = {
  MANUAL:
    'No automatic number. Set businessDocumentNumber when creating the record (or leave empty).',
  TIMESTAMP:
    'Builds a value from the current time (with optional prefix). Suited to low collision rates or display-only codes.',
  TENANT_SEQUENCE:
    'Increments a counter for this tenant and entity. Optional prefix; numeric part is zero-padded to the width below.',
  MONTHLY_SEQUENCE:
    'Each calendar month (in the time zone below) starts a new sequence: optional prefix + YYYYMM + zero-padded counter.',
};

export function readDocGenFromConfig(config: EntityFieldDto['config']): {
  strategy: DocumentNumberGenerationStrategy;
  prefix: string;
  sequenceWidth: number;
  timeZone: string;
} {
  const raw = config?.documentNumberGeneration;
  if (!raw || typeof raw !== 'object') {
    return { strategy: 'MANUAL', prefix: '', sequenceWidth: 4, timeZone: 'UTC' };
  }
  const o = raw as Record<string, unknown>;
  const s = o.strategy;
  const strategy: DocumentNumberGenerationStrategy =
    s === 'TIMESTAMP' || s === 'TENANT_SEQUENCE' || s === 'MONTHLY_SEQUENCE' || s === 'MANUAL'
      ? s
      : 'MANUAL';
  const prefix = typeof o.prefix === 'string' ? o.prefix : '';
  let sequenceWidth = 4;
  if (typeof o.sequenceWidth === 'number' && Number.isFinite(o.sequenceWidth)) {
    sequenceWidth = Math.min(12, Math.max(1, Math.floor(o.sequenceWidth)));
  }
  const timeZone = typeof o.timeZone === 'string' && o.timeZone.trim() ? o.timeZone.trim() : 'UTC';
  return { strategy, prefix, sequenceWidth, timeZone };
}

export function buildDocumentNumberGenerationPayload(
  strategy: DocumentNumberGenerationStrategy,
  prefix: string,
  sequenceWidth: number,
  timeZone: string
): DocumentNumberGenerationConfig {
  const p = prefix.trim();
  if (strategy === 'MANUAL') {
    return { strategy: 'MANUAL' };
  }
  const out: DocumentNumberGenerationConfig = { strategy };
  if (p) out.prefix = p;
  if (strategy === 'TENANT_SEQUENCE' || strategy === 'MONTHLY_SEQUENCE') {
    out.sequenceWidth = sequenceWidth;
  }
  if (strategy === 'MONTHLY_SEQUENCE') {
    out.timeZone = timeZone.trim() || 'UTC';
  }
  return out;
}

/** Field types where stored values are edited as plain text (length limits apply). */
export function fieldTypeSupportsTextLengthConstraints(fieldType: string): boolean {
  const t = fieldType.trim().toLowerCase();
  // `string` is the catalog / entity-builder default for text columns; portal create UI often uses `text` — treat both the same.
  return (
    t === 'string' ||
    t === 'text' ||
    t === 'reference' ||
    t === 'date' ||
    t === 'datetime'
  );
}

export function readConfigLengthString(config: Record<string, unknown> | null | undefined, key: string): string {
  if (!config || !(key in config)) return '';
  const v = config[key];
  if (typeof v === 'number' && Number.isFinite(v) && v >= 0 && Number.isInteger(v)) return String(v);
  if (typeof v === 'string' && /^\d+$/.test(v)) return v;
  return '';
}

export function parseOptionalNonNegativeInt(raw: string): number | undefined {
  const t = raw.trim();
  if (!t) return undefined;
  const n = parseInt(t, 10);
  if (!Number.isFinite(n) || n < 0) return undefined;
  return n;
}

/**
 * Applies validated min/max character limits to field config. Clears keys when unset.
 * @returns error message or null
 */
export function applyTextLengthConstraintsToConfig(
  base: Record<string, unknown>,
  minRaw: string,
  maxRaw: string
): string | null {
  const minOpt = parseOptionalNonNegativeInt(minRaw);
  const maxOpt = parseOptionalNonNegativeInt(maxRaw);
  if (minRaw.trim() && minOpt === undefined) return 'Min length must be a non-negative whole number.';
  if (maxRaw.trim() && maxOpt === undefined) return 'Max length must be a non-negative whole number.';
  if (minOpt !== undefined && maxOpt !== undefined && minOpt > maxOpt) {
    return 'Min length cannot be greater than max length.';
  }
  if (maxOpt !== undefined && maxOpt < 1) return 'Max length must be at least 1 when set.';
  delete base.minLength;
  delete base.maxLength;
  if (minOpt !== undefined && minOpt > 0) base.minLength = minOpt;
  if (maxOpt !== undefined) base.maxLength = maxOpt;
  return null;
}

export function readLengthConstraintFromConfig(
  config: Record<string, unknown> | null | undefined,
  key: 'minLength' | 'maxLength'
): number | undefined {
  if (!config || !(key in config)) return undefined;
  const v = config[key];
  if (typeof v === 'number' && Number.isFinite(v) && v >= 0) return Math.floor(v);
  if (typeof v === 'string' && /^\d+$/.test(v.trim())) return parseInt(v.trim(), 10);
  return undefined;
}

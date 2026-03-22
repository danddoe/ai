import type { EntityFieldDto } from '../api/schemas';

export function isCoreDomainField(f: EntityFieldDto): boolean {
  const storage = (f.config as { storage?: string } | null | undefined)?.storage;
  return Boolean(storage && String(storage).toUpperCase() === 'CORE_DOMAIN');
}

export function readCoreBindingColumn(f: EntityFieldDto): string | null {
  const cfg = f.config as { coreBinding?: { column?: string } } | null | undefined;
  const c = cfg?.coreBinding?.column;
  if (typeof c !== 'string' || !c.trim()) return null;
  return c.trim();
}

/** EAV-backed field for hybrid checks (excludes document_number row field). */
export function isEavExtensionField(
  f: EntityFieldDto,
  isDocumentNumber: (ft: string | null | undefined) => boolean
): boolean {
  return !isCoreDomainField(f) && !isDocumentNumber(f.fieldType);
}

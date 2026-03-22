import type { EntityFieldDto, RecordQueryFilterNode } from '../api/schemas';

/** EAV string-ish fields suitable for a simple top-of-grid text search (OR contains). */
export function filterableTextFieldSlugs(fields: EntityFieldDto[], restrictToSlugs?: string[] | null): string[] {
  const restrict = restrictToSlugs?.length ? new Set(restrictToSlugs) : null;
  const out: string[] = [];
  for (const f of fields) {
    if (restrict && !restrict.has(f.slug)) continue;
    const ft = f.fieldType.toLowerCase();
    if (ft !== 'string' && ft !== 'text') continue;
    const storage = (f.config as { storage?: string } | null | undefined)?.storage;
    if (storage && String(storage).toUpperCase() === 'CORE_DOMAIN') continue;
    out.push(f.slug);
  }
  return out.slice(0, 32);
}

/** OR group of `contains` clauses; null if search text is empty. */
export function buildRecordSearchFilter(fields: EntityFieldDto[], searchText: string, visibleSlugs: string[] | null): RecordQueryFilterNode | null {
  const q = searchText.trim();
  if (!q) return null;
  const slugs = filterableTextFieldSlugs(fields, visibleSlugs?.length ? visibleSlugs : null);
  if (slugs.length === 0) return null;
  return {
    op: 'or',
    children: slugs.map((slug) => ({
      op: 'contains',
      field: slug,
      value: q,
    })),
  };
}

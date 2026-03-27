import type { EntityDto, EntityFieldDto, RecordDto } from '../api/schemas';
import { looksLikeRecordUuid, readReferenceFieldConfig } from './referenceFieldConfig';

export const LIST_SUMMARY_SEPARATOR = ' - ';

/** Preserve schema order; drop duplicate slugs (first occurrence wins). */
export function dedupeSlugsPreserveOrder(columnSlugs: string[]): string[] {
  const seen = new Set<string>();
  const out: string[] = [];
  for (const s of columnSlugs) {
    const t = s.trim();
    if (!t) continue;
    const key = t.toLowerCase();
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(t);
  }
  return out;
}

function formatValueForLabel(v: unknown): string {
  if (v === null || v === undefined) return '—';
  if (typeof v === 'object') return JSON.stringify(v);
  return String(v);
}

function resolveLabelFromRecordValues(
  rec: { values: Record<string, unknown> },
  defaultDisplayFieldSlug: string | null | undefined
): string {
  if (defaultDisplayFieldSlug) {
    const raw = rec.values[defaultDisplayFieldSlug];
    if (raw !== null && raw !== undefined && String(raw).trim() !== '') {
      return String(raw);
    }
  }
  const first = Object.values(rec.values).find((x) => x !== null && x !== undefined && String(x).trim() !== '');
  if (first !== undefined) return String(first);
  return '—';
}

/**
 * Single-line label: {@code referenceLookupDisplaySlugs} joined by {@link LIST_SUMMARY_SEPARATOR}.
 * If no columns configured, falls back to entity default display field, then first non-empty value.
 */
export function buildReferenceRecordLabel(
  rec: RecordDto,
  defaultDisplayFieldSlug: string | null | undefined,
  columnSlugs: string[]
): string {
  const ordered = dedupeSlugsPreserveOrder(columnSlugs);
  if (ordered.length === 0) {
    return resolveLabelFromRecordValues(rec, defaultDisplayFieldSlug);
  }
  return ordered.map((s) => formatValueForLabel(rec.values[s])).join(LIST_SUMMARY_SEPARATOR);
}

function shortId(id: string): string {
  return id.length > 10 ? `${id.slice(0, 8)}…` : id;
}

function formatListSummaryScalar(field: EntityFieldDto, v: unknown): string {
  const ft = (field.fieldType || '').toLowerCase();
  if (v === undefined || v === null) return '—';
  if (ft === 'boolean') return v === true || v === 'true' ? 'Yes' : v === false || v === 'false' ? 'No' : String(v);
  if (typeof v === 'object') return JSON.stringify(v);
  return String(v);
}

/**
 * Basic list Display: concatenate flagged fields in {@code sortOrder}. Uses {@code refLabels} for reference UUIDs
 * (keys {@code fieldSlug::uuid}).
 */
export function buildRecordListSummaryDisplay(
  row: RecordDto,
  summaryFieldsOrdered: EntityFieldDto[],
  refLabels: Record<string, string>
): string {
  const parts: string[] = [];
  for (const f of summaryFieldsOrdered) {
    const v = row.values[f.slug];
    if (v === undefined || v === null || String(v).trim() === '') continue;
    const ft = (f.fieldType || '').toLowerCase();
    if (ft === 'reference') {
      const uuid = String(v).trim();
      const key = `${f.slug}::${uuid}`;
      const lbl = refLabels[key]?.trim();
      if (lbl) parts.push(lbl);
      else if (looksLikeRecordUuid(uuid)) parts.push(shortId(uuid));
    } else {
      const s = formatListSummaryScalar(f, v);
      if (s && s !== '—') parts.push(s);
    }
  }
  const joined = parts.filter(Boolean).join(LIST_SUMMARY_SEPARATOR);
  return joined || '—';
}

/** Map slug → entity for reference targets (slug lowercased). */
export function entitiesBySlugLower(entities: EntityDto[]): Record<string, EntityDto> {
  const m: Record<string, EntityDto> = {};
  for (const e of entities) {
    const k = (e.slug || '').trim().toLowerCase();
    if (k) m[k] = e;
  }
  return m;
}

export function collectReferenceResolutionTasks(
  fields: EntityFieldDto[],
  fieldSlugs: Iterable<string>,
  entitiesBySlug: Record<string, EntityDto>
): { field: EntityFieldDto; targetEntityId: string }[] {
  const slugSet = new Set<string>();
  for (const s of fieldSlugs) {
    const t = s.trim();
    if (t) slugSet.add(t);
  }
  const out: { field: EntityFieldDto; targetEntityId: string }[] = [];
  for (const f of fields) {
    if (!slugSet.has(f.slug)) continue;
    if ((f.fieldType || '').toLowerCase() !== 'reference') continue;
    const slug = readReferenceFieldConfig(f.config).targetEntitySlug;
    const ent = entitiesBySlug[slug];
    if (ent?.id) out.push({ field: f, targetEntityId: ent.id });
  }
  return out;
}

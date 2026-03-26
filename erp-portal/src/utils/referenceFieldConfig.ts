import type { EntityDto, EntityFieldDto } from '../api/schemas';
import { ENTITY_STATUS_ENTITY_SLUG } from './entityStatusCatalog';

export const MAX_REFERENCE_LOOKUP_COLUMNS = 12;

/** How the record form picks a related record: modal search vs dropdown of recent rows. */
export type ReferenceUiMode = 'search' | 'dropdown';

export function readReferenceFieldConfig(cfg: EntityFieldDto['config']): {
  targetEntitySlug: string;
  lookupDisplaySlugs: string[];
  uiMode: ReferenceUiMode;
} {
  const raw = cfg && typeof cfg === 'object' ? (cfg as Record<string, unknown>) : {};
  /** Slugs are normalized lowercase so they match {@link EntityDto.slug} from the API. */
  const ts =
    typeof raw.targetEntitySlug === 'string' ? raw.targetEntitySlug.trim().toLowerCase() : '';
  const slugs = Array.isArray(raw.referenceLookupDisplaySlugs)
    ? raw.referenceLookupDisplaySlugs
        .filter((s): s is string => typeof s === 'string' && s.trim().length > 0)
        .map((s) => s.trim())
        .slice(0, MAX_REFERENCE_LOOKUP_COLUMNS)
    : [];
  const uiMode: ReferenceUiMode = raw.referenceUiMode === 'dropdown' ? 'dropdown' : 'search';
  return { targetEntitySlug: ts, lookupDisplaySlugs: slugs, uiMode };
}

/** True when this reference targets the platform {@code entity_status} mirror entity. */
export function isEntityStatusReferenceTarget(targetEntitySlug: string): boolean {
  return targetEntitySlug.trim().toLowerCase() === ENTITY_STATUS_ENTITY_SLUG;
}

const UUID_RE =
  /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/;

export function looksLikeRecordUuid(s: string): boolean {
  return UUID_RE.test(s.trim());
}

/** Build a slug → entity map for reference fields on the current form. */
export function buildEntityBySlugForReferenceFields(
  fields: EntityFieldDto[],
  allEntities: EntityDto[]
): Record<string, EntityDto> {
  const slugs = new Set<string>();
  for (const f of fields) {
    if ((f.fieldType || '').toLowerCase() !== 'reference') continue;
    const { targetEntitySlug } = readReferenceFieldConfig(f.config);
    if (targetEntitySlug) slugs.add(targetEntitySlug);
  }
  const map: Record<string, EntityDto> = {};
  for (const e of allEntities) {
    const k = (e.slug || '').trim().toLowerCase();
    if (k && slugs.has(k)) map[k] = e;
  }
  return map;
}

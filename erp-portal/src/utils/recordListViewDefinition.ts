/** Matches entity-builder RecordListViewJsonValidator v1. */

/** Field slug that duplicates the record row UUID; the Records page always shows that id separately, not as a designed column. */
export const RECORD_LIST_ROW_ID_SLUG = 'id';

export type RecordListColumnDefinition = {
  fieldSlug: string;
  order: number;
  label?: string | null;
  width?: string | number;
  align?: 'left' | 'center' | 'right';
  inlineEditable?: boolean;
  linkToRecord?: boolean;
  visible?: boolean;
};

export type RecordListViewDefinitionV1 = {
  version: 1;
  columns: RecordListColumnDefinition[];
  showRowActions?: boolean;
  /** When false, the Records grid omits the leading UUID column. Default true. */
  showRecordId?: boolean;
};

function readDefinitionRoot(raw: unknown, depth = 0): Record<string, unknown> | null {
  if (depth > 5) return null;
  if (raw == null) return null;
  if (typeof raw === 'string') {
    try {
      const inner = JSON.parse(raw) as unknown;
      return readDefinitionRoot(inner, depth + 1);
    } catch {
      return null;
    }
  }
  if (typeof raw !== 'object') return null;
  const o = raw as Record<string, unknown>;
  const keys = Object.keys(o);
  /** Entity-builder {@code RecordListViewsController} wraps unparseable JSON as {@code { raw: string }}. */
  if (keys.length === 1 && keys[0] === 'raw') {
    return readDefinitionRoot(o.raw, depth + 1);
  }
  return o;
}

function parseColumnOrder(value: unknown, fallback: number): number {
  if (typeof value === 'number' && Number.isFinite(value)) return Math.trunc(value);
  if (typeof value === 'string' && value.trim()) {
    const n = Number.parseInt(value.trim(), 10);
    if (Number.isFinite(n)) return n;
  }
  return fallback;
}

function columnFieldSlug(col: Record<string, unknown>): string {
  const a = col.fieldSlug;
  const b = col.field_slug;
  if (typeof a === 'string' && a.trim()) return a.trim();
  if (typeof b === 'string' && b.trim()) return b.trim();
  return '';
}

/** Accepts boolean, or string/number forms some gateways or stores emit. */
function parseBoolish(value: unknown, defaultValue: boolean): boolean {
  if (typeof value === 'boolean') return value;
  if (typeof value === 'number' && Number.isFinite(value)) return value !== 0;
  if (typeof value === 'string') {
    const s = value.trim().toLowerCase();
    if (s === 'true' || s === '1' || s === 'yes') return true;
    if (s === 'false' || s === '0' || s === 'no') return false;
  }
  return defaultValue;
}

function parseVersion(value: unknown): number | null {
  if (typeof value === 'number' && Number.isFinite(value)) return value === 1 ? 1 : null;
  if (typeof value === 'string' && value.trim()) {
    const n = Number(value.trim());
    return Number.isFinite(n) && n === 1 ? 1 : null;
  }
  return null;
}

function normalizeColumnsArray(value: unknown): unknown[] | null {
  if (Array.isArray(value)) return value;
  if (typeof value === 'string' && value.trim()) {
    try {
      const inner = JSON.parse(value) as unknown;
      return Array.isArray(inner) ? inner : null;
    } catch {
      return null;
    }
  }
  return null;
}

export function parseRecordListViewDefinition(raw: unknown): RecordListViewDefinitionV1 | null {
  const o = readDefinitionRoot(raw);
  if (!o) return null;
  if (parseVersion(o.version) !== 1) return null;
  const cols = normalizeColumnsArray(o.columns);
  if (!cols) return null;
  const columns: RecordListColumnDefinition[] = [];
  for (const c of cols) {
    if (!c || typeof c !== 'object') continue;
    const col = c as Record<string, unknown>;
    const slug = columnFieldSlug(col);
    if (!slug) continue;
    const order = parseColumnOrder(col.order, columns.length);
    const def: RecordListColumnDefinition = { fieldSlug: slug, order };
    if (typeof col.label === 'string') def.label = col.label;
    if (col.width !== undefined && col.width !== null) def.width = col.width as string | number;
    const align = col.align;
    if (align === 'left' || align === 'center' || align === 'right') def.align = align;
    const inlineEditable = col.inlineEditable ?? col.inline_editable;
    if (typeof inlineEditable === 'boolean') def.inlineEditable = inlineEditable;
    const linkToRecord = col.linkToRecord ?? col.link_to_record;
    if (typeof linkToRecord === 'boolean') def.linkToRecord = linkToRecord;
    const visible = col.visible;
    if (typeof visible === 'boolean') def.visible = visible;
    columns.push(def);
  }
  columns.sort((a, b) => a.order - b.order);
  const showRowActions = parseBoolish(o.showRowActions ?? o.show_row_actions, true);
  const showRecordId = parseBoolish(o.showRecordId ?? o['show_record_id'] ?? o.show_record_id, true);
  return { version: 1, columns: withoutRowIdSlugColumns(columns), showRowActions, showRecordId };
}

/** Drops the record-PK field slug from column definitions; use definition `showRecordId` to show or hide the UUID on Records. */
export function withoutRowIdSlugColumns(columns: RecordListColumnDefinition[]): RecordListColumnDefinition[] {
  return columns
    .filter((c) => c.fieldSlug.trim().toLowerCase() !== RECORD_LIST_ROW_ID_SLUG)
    .map((c, i) => ({ ...c, order: i }));
}

export function buildRecordListViewDefinitionV1(
  columns: RecordListColumnDefinition[],
  showRowActions: boolean,
  showRecordId = true
): RecordListViewDefinitionV1 {
  return {
    version: 1,
    columns: withoutRowIdSlugColumns(columns),
    showRowActions,
    showRecordId,
  };
}

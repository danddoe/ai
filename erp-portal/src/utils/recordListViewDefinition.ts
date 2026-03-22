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

function readDefinitionRoot(raw: unknown): Record<string, unknown> | null {
  if (raw == null) return null;
  if (typeof raw === 'string') {
    try {
      const inner = JSON.parse(raw) as unknown;
      return typeof inner === 'object' && inner !== null ? (inner as Record<string, unknown>) : null;
    } catch {
      return null;
    }
  }
  if (typeof raw === 'object') return raw as Record<string, unknown>;
  return null;
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

export function parseRecordListViewDefinition(raw: unknown): RecordListViewDefinitionV1 | null {
  const o = readDefinitionRoot(raw);
  if (!o) return null;
  const ver = Number(o.version);
  if (!Number.isFinite(ver) || ver !== 1) return null;
  const cols = o.columns;
  if (!Array.isArray(cols)) return null;
  const columns: RecordListColumnDefinition[] = [];
  for (const c of cols) {
    if (!c || typeof c !== 'object') continue;
    const col = c as Record<string, unknown>;
    const slug = typeof col.fieldSlug === 'string' ? col.fieldSlug.trim() : '';
    if (!slug) continue;
    const order = typeof col.order === 'number' && Number.isFinite(col.order) ? col.order : columns.length;
    const def: RecordListColumnDefinition = { fieldSlug: slug, order };
    if (typeof col.label === 'string') def.label = col.label;
    if (col.width !== undefined && col.width !== null) def.width = col.width as string | number;
    if (col.align === 'left' || col.align === 'center' || col.align === 'right') def.align = col.align;
    if (typeof col.inlineEditable === 'boolean') def.inlineEditable = col.inlineEditable;
    if (typeof col.linkToRecord === 'boolean') def.linkToRecord = col.linkToRecord;
    if (typeof col.visible === 'boolean') def.visible = col.visible;
    columns.push(def);
  }
  columns.sort((a, b) => a.order - b.order);
  const showRowActions = parseBoolish(o.showRowActions, true);
  const showRecordId = parseBoolish(o.showRecordId ?? o['show_record_id'], true);
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

/** Matches entity-builder RecordListViewJsonValidator v1. */

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
};

export function parseRecordListViewDefinition(raw: unknown): RecordListViewDefinitionV1 | null {
  if (!raw || typeof raw !== 'object') return null;
  const o = raw as Record<string, unknown>;
  if (o.version !== 1) return null;
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
  const showRowActions = typeof o.showRowActions === 'boolean' ? o.showRowActions : true;
  return { version: 1, columns, showRowActions };
}

export function buildRecordListViewDefinitionV1(
  columns: RecordListColumnDefinition[],
  showRowActions: boolean
): RecordListViewDefinitionV1 {
  const normalized = columns.map((c, i) => ({
    ...c,
    order: typeof c.order === 'number' ? c.order : i,
  }));
  return { version: 1, columns: normalized, showRowActions };
}

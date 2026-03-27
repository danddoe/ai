import type {
  LayoutItem,
  LayoutItemAction,
  LayoutRegionBinding,
  LayoutV2,
  Presentation,
  RegionRole,
} from '../types/formLayout';
import { isActionItem, newColumn, newRegion, newRow } from './layoutV2';
import { newId as nid } from './newId';

function moveInArray<T>(arr: T[], i: number, delta: number): T[] {
  const j = i + delta;
  if (j < 0 || j >= arr.length) return arr;
  const next = [...arr];
  [next[i], next[j]] = [next[j], next[i]];
  return next;
}

function cloneLayout(layout: LayoutV2): LayoutV2 {
  return structuredClone(layout);
}

export function moveRegion(layout: LayoutV2, index: number, delta: number): LayoutV2 {
  const next = cloneLayout(layout);
  next.regions = moveInArray(next.regions, index, delta);
  return next;
}

export function addRegion(layout: LayoutV2, role: RegionRole): LayoutV2 {
  const next = cloneLayout(layout);
  const title =
    role === 'header' ? 'Header' : role === 'tab' ? 'Tab' : 'Detail';
  const tabId = role === 'tab' ? `tabs-${nid('tg')}` : null;
  next.regions.push(newRegion(role, title, tabId));
  return next;
}

export function removeRegion(layout: LayoutV2, index: number): LayoutV2 {
  const next = cloneLayout(layout);
  next.regions.splice(index, 1);
  return next;
}

export function addRow(layout: LayoutV2, regionIndex: number): LayoutV2 {
  const next = cloneLayout(layout);
  next.regions[regionIndex].rows.push(newRow());
  return next;
}

export function moveRow(layout: LayoutV2, regionIndex: number, rowIndex: number, delta: number): LayoutV2 {
  const next = cloneLayout(layout);
  const rows = next.regions[regionIndex].rows;
  next.regions[regionIndex].rows = moveInArray(rows, rowIndex, delta);
  return next;
}

export function removeRow(layout: LayoutV2, regionIndex: number, rowIndex: number): LayoutV2 {
  const next = cloneLayout(layout);
  next.regions[regionIndex].rows.splice(rowIndex, 1);
  return next;
}

export function addColumn(layout: LayoutV2, regionIndex: number, rowIndex: number): LayoutV2 {
  const next = cloneLayout(layout);
  const row = next.regions[regionIndex].rows[rowIndex];
  const n = row.columns.length;
  const span = Math.max(1, Math.floor(12 / (n + 1)));
  row.columns = row.columns.map((c) => ({ ...c, span }));
  row.columns.push(newColumn(span));
  return next;
}

export function moveColumn(
  layout: LayoutV2,
  regionIndex: number,
  rowIndex: number,
  columnIndex: number,
  delta: number
): LayoutV2 {
  const next = cloneLayout(layout);
  const cols = next.regions[regionIndex].rows[rowIndex].columns;
  next.regions[regionIndex].rows[rowIndex].columns = moveInArray(cols, columnIndex, delta);
  return next;
}

export function removeColumn(layout: LayoutV2, regionIndex: number, rowIndex: number, columnIndex: number): LayoutV2 {
  const next = cloneLayout(layout);
  const row = next.regions[regionIndex].rows[rowIndex];
  row.columns.splice(columnIndex, 1);
  if (row.columns.length === 0) {
    row.columns.push(newColumn(12));
  } else {
    const span = Math.floor(12 / row.columns.length);
    let rest = 12 - span * row.columns.length;
    row.columns = row.columns.map((c, i) => ({
      ...c,
      span: span + (i === 0 ? rest : 0),
    }));
  }
  return next;
}

export function addItem(
  layout: LayoutV2,
  regionIndex: number,
  rowIndex: number,
  columnIndex: number,
  item: LayoutItem
): LayoutV2 {
  const next = cloneLayout(layout);
  next.regions[regionIndex].rows[rowIndex].columns[columnIndex].items.push(item);
  return next;
}

export function moveItem(
  layout: LayoutV2,
  regionIndex: number,
  rowIndex: number,
  columnIndex: number,
  itemIndex: number,
  delta: number
): LayoutV2 {
  const next = cloneLayout(layout);
  const items = next.regions[regionIndex].rows[rowIndex].columns[columnIndex].items;
  next.regions[regionIndex].rows[rowIndex].columns[columnIndex].items = moveInArray(items, itemIndex, delta);
  return next;
}

export function removeItem(
  layout: LayoutV2,
  regionIndex: number,
  rowIndex: number,
  columnIndex: number,
  itemIndex: number
): LayoutV2 {
  const next = cloneLayout(layout);
  next.regions[regionIndex].rows[rowIndex].columns[columnIndex].items.splice(itemIndex, 1);
  return next;
}

export function updateItemPresentation(
  layout: LayoutV2,
  regionIndex: number,
  rowIndex: number,
  columnIndex: number,
  itemIndex: number,
  presentation: Presentation
): LayoutV2 {
  const cur = layout.regions[regionIndex]?.rows[rowIndex]?.columns[columnIndex]?.items[itemIndex];
  if (!cur || isActionItem(cur)) return layout;
  const next = cloneLayout(layout);
  const item = next.regions[regionIndex].rows[rowIndex].columns[columnIndex].items[itemIndex];
  if (isActionItem(item)) return layout;
  item.presentation = { ...presentation };
  return next;
}

export function updateLayoutActionItem(
  layout: LayoutV2,
  regionIndex: number,
  rowIndex: number,
  columnIndex: number,
  itemIndex: number,
  patch: Partial<Pick<LayoutItemAction, 'action' | 'label' | 'href' | 'openInNewTab' | 'variant'>>
): LayoutV2 {
  const cur = layout.regions[regionIndex]?.rows[rowIndex]?.columns[columnIndex]?.items[itemIndex];
  if (!cur || !isActionItem(cur)) return layout;
  const next = cloneLayout(layout);
  const item = next.regions[regionIndex].rows[rowIndex].columns[columnIndex].items[itemIndex];
  if (!isActionItem(item)) return layout;
  Object.assign(item, patch);
  return next;
}

export function bindItemToField(
  layout: LayoutV2,
  regionIndex: number,
  rowIndex: number,
  columnIndex: number,
  itemIndex: number,
  fieldId: string,
  fieldSlug: string
): LayoutV2 {
  const cur = layout.regions[regionIndex]?.rows[rowIndex]?.columns[columnIndex]?.items[itemIndex];
  if (!cur || isActionItem(cur)) return layout;
  const next = cloneLayout(layout);
  const item = next.regions[regionIndex].rows[rowIndex].columns[columnIndex].items[itemIndex];
  if (isActionItem(item)) return layout;
  item.fieldId = fieldId;
  item.fieldSlug = fieldSlug;
  return next;
}

export function setRegionTitle(layout: LayoutV2, regionIndex: number, title: string): LayoutV2 {
  const next = cloneLayout(layout);
  next.regions[regionIndex].title = title;
  return next;
}

export function setRegionBinding(
  layout: LayoutV2,
  regionIndex: number,
  binding: LayoutRegionBinding | null
): LayoutV2 {
  const next = cloneLayout(layout);
  const r = next.regions[regionIndex];
  if (binding == null) {
    delete r.binding;
  } else {
    r.binding = binding;
  }
  return next;
}

export function setColumnSpan(
  layout: LayoutV2,
  regionIndex: number,
  rowIndex: number,
  columnIndex: number,
  span: number
): LayoutV2 {
  const next = cloneLayout(layout);
  next.regions[regionIndex].rows[rowIndex].columns[columnIndex].span = Math.min(12, Math.max(1, span));
  return next;
}

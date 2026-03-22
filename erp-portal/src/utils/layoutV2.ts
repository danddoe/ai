import type { EntityFieldDto } from '../api/schemas';
import type {
  LayoutActionType,
  LayoutActionVariant,
  LayoutColumn,
  LayoutItem,
  LayoutItemAction,
  LayoutRegion,
  LayoutRow,
  LayoutV2,
  Presentation,
  RegionRole,
} from '../types/formLayout';
import { newId } from './newId';

/** Regions shown for wizard step `stepIndex` (matches `runtime.recordEntry.wizard.stepOrderRegionIds`). */
export function regionsForWizardStep(layout: LayoutV2, stepIndex: number): LayoutRegion[] {
  const ids = layout.runtime?.recordEntry?.wizard?.stepOrderRegionIds ?? [];
  const id = ids[stepIndex];
  if (!id) return [];
  const r = layout.regions.find((x) => x.id === id);
  return r ? [r] : [];
}

export function defaultPresentation(): Presentation {
  return {
    label: null,
    placeholder: '',
    helpText: '',
    readOnly: false,
    hidden: false,
    width: 'full',
    componentHint: 'default',
  };
}

export function isActionItem(item: LayoutItem): item is LayoutItemAction {
  return item.kind === 'action';
}

export function newLayoutItem(fieldId: string, fieldSlug: string): LayoutItem {
  return {
    id: newId('item'),
    fieldId,
    fieldSlug,
    presentation: defaultPresentation(),
  };
}

const DEFAULT_ACTION_LABELS: Record<LayoutActionType, string> = {
  save: 'Save',
  cancel: 'Cancel',
  link: 'Link',
};

const DEFAULT_ACTION_VARIANT: Record<LayoutActionType, LayoutActionVariant> = {
  save: 'primary',
  cancel: 'secondary',
  link: 'link',
};

export function newLayoutActionItem(
  action: LayoutActionType,
  overrides?: Partial<Pick<LayoutItemAction, 'label' | 'href' | 'openInNewTab' | 'variant'>>
): LayoutItemAction {
  return {
    kind: 'action',
    id: newId('item'),
    action,
    label: overrides?.label?.trim() || DEFAULT_ACTION_LABELS[action],
    href: overrides?.href,
    openInNewTab: overrides?.openInNewTab,
    variant: overrides?.variant ?? DEFAULT_ACTION_VARIANT[action],
  };
}

/** Aligns with server {@code FormLayoutJsonValidator} link rules: https URL or absolute path, not protocol-relative. */
export function isSafeActionHref(href: string): boolean {
  const t = href.trim();
  if (!t) return false;
  if (t.startsWith('/') && !t.startsWith('//')) return true;
  try {
    const u = new URL(t);
    return u.protocol === 'https:' || u.protocol === 'http:';
  } catch {
    return false;
  }
}

export function newColumn(span = 12): LayoutColumn {
  return { id: newId('col'), span, items: [] };
}

export function newRow(): LayoutRow {
  return { id: newId('row'), columns: [newColumn(12)] };
}

export function newRegion(role: RegionRole, title: string, tabGroupId: string | null = null): LayoutRegion {
  return {
    id: newId('reg'),
    role,
    title,
    tabGroupId,
    rows: [],
  };
}

export function blankLayoutV2(): LayoutV2 {
  return {
    version: 2,
    regions: [
      newRegion('header', 'Summary', null),
      newRegion('tab', 'General', 'main-tabs'),
      newRegion('detail', 'Details', null),
    ],
  };
}

export function isLayoutV2(raw: unknown): raw is Record<string, unknown> & { version: number; regions: unknown } {
  if (!raw || typeof raw !== 'object') return false;
  const o = raw as Record<string, unknown>;
  return Number(o.version) === 2 && Array.isArray(o.regions);
}

/** Normalize API JSON into LayoutV2 with safe defaults (mutates copies only via structuredClone). */
export function parseLayoutV2(raw: unknown): LayoutV2 | null {
  if (!isLayoutV2(raw)) return null;
  try {
    const clone = structuredClone(raw) as LayoutV2;
    if (!Array.isArray(clone.regions)) return null;
    for (const region of clone.regions) {
      if (!region.rows) region.rows = [];
      for (const row of region.rows) {
        if (!row.columns) row.columns = [];
        for (const col of row.columns) {
          if (!col.items) col.items = [];
          for (const item of col.items) {
            if (isActionItem(item)) {
              const a = item.action;
              if (a !== 'save' && a !== 'cancel' && a !== 'link') {
                (item as LayoutItemAction).action = 'save';
              }
              if (!item.label?.trim()) {
                item.label = DEFAULT_ACTION_LABELS[item.action];
              }
              item.variant = item.variant ?? DEFAULT_ACTION_VARIANT[item.action];
            } else if (!item.presentation) {
              item.presentation = defaultPresentation();
            }
          }
        }
      }
    }
    return clone;
  } catch {
    return null;
  }
}

/** Schema field for this placement, or null if missing / orphan / stale id or slug. */
export function resolveLayoutItemField(item: LayoutItem, fields: EntityFieldDto[]): EntityFieldDto | null {
  if (isActionItem(item)) return null;
  if (item.fieldId) {
    const byId = fields.find((f) => f.id === item.fieldId);
    if (byId) return byId;
  }
  if (item.fieldSlug) {
    const bySlug = fields.find((f) => f.slug === item.fieldSlug);
    if (bySlug) return bySlug;
  }
  return null;
}

export function fieldOnForm(layout: LayoutV2, fieldId: string, slug: string): boolean {
  for (const r of layout.regions) {
    for (const row of r.rows) {
      for (const col of row.columns) {
        for (const it of col.items) {
          if (isActionItem(it)) continue;
          if (it.fieldId === fieldId) return true;
          if (it.fieldSlug && it.fieldSlug === slug) return true;
        }
      }
    }
  }
  return false;
}

export function countItemsInRegion(region: LayoutRegion): number {
  let n = 0;
  for (const row of region.rows) {
    for (const col of row.columns) {
      n += col.items.length;
    }
  }
  return n;
}

export function countItemsInRow(row: LayoutRow): number {
  let n = 0;
  for (const col of row.columns) {
    n += col.items.length;
  }
  return n;
}

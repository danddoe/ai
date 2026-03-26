import type { EntityFieldDto } from '../api/schemas';
import type { LayoutV2 } from '../types/formLayout';
import {
  newLayoutActionItem,
  newLayoutItem,
  newRegion,
  newRow,
} from '../utils/layoutV2';
import { newId } from '../utils/newId';

const TEXTISH_TYPES = new Set([
  'text',
  'string',
  'long_text',
  'longtext',
  'email',
  'url',
  'phone',
  'markdown',
]);

/**
 * Deterministic column slugs for a first-pass list (entity field order, text-like fields first).
 */
export function suggestListColumnSlugs(fields: EntityFieldDto[], maxCols = 8): string[] {
  const active = fields.filter((f) => (f.status || 'ACTIVE').toUpperCase() === 'ACTIVE');
  const sorted = [...active].sort((a, b) => a.sortOrder - b.sortOrder || a.name.localeCompare(b.name));
  sorted.sort((a, b) => {
    const ta = TEXTISH_TYPES.has(a.fieldType.trim().toLowerCase()) ? 0 : 1;
    const tb = TEXTISH_TYPES.has(b.fieldType.trim().toLowerCase()) ? 0 : 1;
    return ta - tb || a.sortOrder - b.sortOrder;
  });
  return sorted
    .map((f) => f.slug)
    .filter((s) => s && s.trim().toLowerCase() !== 'id')
    .slice(0, maxCols);
}

export type AutoFormMode = 'single_page' | 'wizard';

const WIZARD_FIELDS_PER_STEP = 5;

/**
 * Auto form layout: single tab region or wizard steps with save/cancel on the last step.
 */
export function buildAutoFormLayoutV2(fields: EntityFieldDto[], mode: AutoFormMode): LayoutV2 {
  const active = fields.filter((f) => (f.status || 'ACTIVE').toUpperCase() === 'ACTIVE');
  const sorted = [...active].sort((a, b) => a.sortOrder - b.sortOrder || a.name.localeCompare(b.name));

  if (mode === 'single_page') {
    const reg = newRegion('tab', 'General', 'main-tabs');
    for (const f of sorted) {
      reg.rows.push({
        id: newId('row'),
        columns: [{ id: newId('col'), span: 12, items: [newLayoutItem(f.id, f.slug)] }],
      });
    }
    const actionRow = newRow();
    actionRow.columns[0].items.push(newLayoutActionItem('save'), newLayoutActionItem('cancel'));
    reg.rows.push(actionRow);
    return { version: 2, regions: [reg], runtime: { recordEntry: { flow: 'free' } } };
  }

  const regions: LayoutV2['regions'] = [];
  for (let i = 0; i < sorted.length; i += WIZARD_FIELDS_PER_STEP) {
    const chunk = sorted.slice(i, i + WIZARD_FIELDS_PER_STEP);
    const reg = newRegion('tab', `Step ${regions.length + 1}`, 'main-tabs');
    for (const f of chunk) {
      reg.rows.push({
        id: newId('row'),
        columns: [{ id: newId('col'), span: 12, items: [newLayoutItem(f.id, f.slug)] }],
      });
    }
    const isLast = i + WIZARD_FIELDS_PER_STEP >= sorted.length;
    if (isLast) {
      const actionRow = newRow();
      actionRow.columns[0].items.push(newLayoutActionItem('save'), newLayoutActionItem('cancel'));
      reg.rows.push(actionRow);
    }
    regions.push(reg);
  }

  if (regions.length === 0) {
    const reg = newRegion('tab', 'General', 'main-tabs');
    const actionRow = newRow();
    actionRow.columns[0].items.push(newLayoutActionItem('save'), newLayoutActionItem('cancel'));
    reg.rows.push(actionRow);
    return {
      version: 2,
      regions: [reg],
      runtime: { recordEntry: { flow: 'wizard', wizard: { stepOrderRegionIds: [reg.id] } } },
    };
  }

  const stepOrderRegionIds = regions.map((r) => r.id);
  return {
    version: 2,
    regions,
    runtime: { recordEntry: { flow: 'wizard', wizard: { stepOrderRegionIds } } },
  };
}

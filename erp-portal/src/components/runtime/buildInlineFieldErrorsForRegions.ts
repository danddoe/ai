import { isDocumentNumberFieldType, type EntityFieldDto } from '../../api/schemas';
import type { LayoutRegion } from '../../types/formLayout';
import type { FieldUiOverrides } from '../../utils/businessRuleUi';
import {
  fieldTypeSupportsTextLengthConstraints,
  readLengthConstraintFromConfig,
} from '../../utils/fieldTextConstraints';
import { isActionItem, resolveLayoutItemField } from '../../utils/layoutV2';

function fieldDisplayLabel(field: EntityFieldDto): string {
  const d = field.displayLabel?.trim();
  if (d) return d;
  return field.labelOverride?.trim() || field.name;
}

/**
 * Required-field and text-length validation for visible layout regions.
 * Returns a map of field slug → message for inline display under inputs.
 */
export function buildInlineFieldErrorsForRegions(
  regions: LayoutRegion[],
  fields: EntityFieldDto[],
  values: Record<string, unknown>,
  fieldUiOverrides?: FieldUiOverrides
): Record<string, string> {
  const errors: Record<string, string> = {};

  const seenRequired = new Set<string>();
  for (const region of regions) {
    for (const row of region.rows) {
      for (const col of row.columns) {
        for (const item of col.items) {
          if (isActionItem(item)) continue;
          const field = resolveLayoutItemField(item, fields);
          if (!field) continue;
          const slug = field.slug;
          const ui = fieldUiOverrides?.[slug];
          if (!field.required && !ui?.required) continue;
          if (isDocumentNumberFieldType(field.fieldType)) continue;
          if (item.presentation?.hidden || ui?.hidden) continue;
          if (seenRequired.has(slug)) continue;
          seenRequired.add(slug);
          const v = values[slug];
          if (v === undefined || v === null || v === '') {
            errors[slug] = `${fieldDisplayLabel(field)} is required.`;
          }
        }
      }
    }
  }

  const seenLen = new Set<string>();
  for (const region of regions) {
    for (const row of region.rows) {
      for (const col of row.columns) {
        for (const item of col.items) {
          if (isActionItem(item)) continue;
          const field = resolveLayoutItemField(item, fields);
          if (!field || item.presentation?.hidden) continue;
          const slug = field.slug;
          if (fieldUiOverrides?.[slug]?.hidden) continue;
          if (!fieldTypeSupportsTextLengthConstraints(field.fieldType)) continue;
          if (seenLen.has(slug)) continue;
          seenLen.add(slug);
          if (errors[slug]) continue;
          const cfg = (field.config ?? undefined) as Record<string, unknown> | undefined;
          const maxL = readLengthConstraintFromConfig(cfg, 'maxLength');
          const minL = readLengthConstraintFromConfig(cfg, 'minLength');
          if (maxL === undefined && (minL === undefined || minL <= 0)) continue;
          const raw = values[slug];
          const str = raw === null || raw === undefined ? '' : String(raw);
          if (maxL !== undefined && str.length > maxL) {
            errors[slug] = `At most ${maxL} characters.`;
          } else if (minL !== undefined && minL > 0 && str.length > 0 && str.length < minL) {
            errors[slug] = `At least ${minL} characters.`;
          }
        }
      }
    }
  }

  return errors;
}

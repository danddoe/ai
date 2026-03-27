/**
 * Client-side evaluation of business rule conditions (must stay aligned with
 * {@code RuleConditionEvaluator} in entity-builder).
 *
 * Condition JSON:
 * - { op: "and" | "or", children: Condition[] }
 * - { op: "cmp", field, operator, value? }  operator: eq|neq|gt|gte|lt|lte|isEmpty|isNotEmpty
 */

export type RuleCondition =
  | { op: 'and' | 'or'; children: RuleCondition[] }
  | { op: 'cmp'; field: string; operator: string; value?: unknown };

export type BusinessRuleTrigger = 'BEFORE_CREATE' | 'BEFORE_UPDATE' | 'ON_FORM_LOAD' | 'ON_FORM_CHANGE';

export type BusinessRuleActionType =
  | 'SERVER_SET_FIELD_VALUE'
  | 'SERVER_ADD_ERROR'
  | 'UI_SET_FIELD_VISIBILITY'
  | 'UI_SET_FIELD_READ_ONLY'
  | 'UI_SET_FIELD_REQUIRED';

export type BusinessRuleActionDto = {
  id: string;
  priority: number;
  actionType: BusinessRuleActionType;
  payload: Record<string, unknown>;
  applyUi: boolean;
  applyServer: boolean;
};

export type BusinessRuleDto = {
  id: string;
  tenantId: string;
  entityId: string;
  formLayoutId: string | null;
  name: string;
  description: string | null;
  priority: number;
  trigger: BusinessRuleTrigger;
  condition: RuleCondition | Record<string, unknown>;
  active: boolean;
  actions: BusinessRuleActionDto[];
  createdAt: string;
  updatedAt: string;
};

export type FieldUiOverrides = Record<
  string,
  { hidden?: boolean; readOnly?: boolean; required?: boolean }
>;

function isEmptyValue(v: unknown): boolean {
  if (v === null || v === undefined) return true;
  if (typeof v === 'string') return v.trim() === '';
  return false;
}

function normalizeUuidish(v: unknown): unknown {
  if (typeof v === 'string') {
    const u = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
    if (u.test(v)) return v.toLowerCase();
  }
  return v;
}

function compareEqual(a: unknown, e: unknown): boolean {
  const na = normalizeUuidish(a);
  const ne = normalizeUuidish(e);
  if (typeof na === 'number' && typeof ne === 'number') return na === ne;
  return na === ne;
}

function coerceNumber(v: unknown): number | null {
  if (v === null || v === undefined) return null;
  if (typeof v === 'number' && !Number.isNaN(v)) return v;
  const n = Number(String(v).trim());
  return Number.isNaN(n) ? null : n;
}

/** Exported for tests; matches server-side {@code RuleConditionEvaluator} behavior. */
export function evalCondition(cond: RuleCondition | Record<string, unknown>, values: Record<string, unknown>): boolean {
  const c = cond as RuleCondition & { op?: string };
  const op = c.op;
  if (!op) return false;
  if (op === 'and') {
    const children = (c as { children?: RuleCondition[] }).children ?? [];
    return children.every((ch) => evalCondition(ch as RuleCondition, values));
  }
  if (op === 'or') {
    const children = (c as { children?: RuleCondition[] }).children ?? [];
    return children.some((ch) => evalCondition(ch as RuleCondition, values));
  }
  if (op === 'cmp') {
    const field = (c as { field?: string }).field;
    const operator = String((c as { operator?: string }).operator || '').toLowerCase();
    if (!field || !operator) return false;
    const actual = values[field];
    const expected = (c as { value?: unknown }).value;
    switch (operator) {
      case 'isempty':
        return isEmptyValue(actual);
      case 'isnotempty':
        return !isEmptyValue(actual);
      case 'eq':
        return compareEqual(actual, expected);
      case 'neq':
        return !compareEqual(actual, expected);
      case 'gt':
      case 'gte':
      case 'lt':
      case 'lte': {
        const a = coerceNumber(actual);
        const b = coerceNumber(expected);
        if (a === null || b === null) throw new Error('Numeric comparison requires numeric values');
        if (operator === 'gt') return a > b;
        if (operator === 'gte') return a >= b;
        if (operator === 'lt') return a < b;
        return a <= b;
      }
      default:
        throw new Error(`Unsupported cmp operator: ${operator}`);
    }
  }
  return false;
}

/** UI triggers only; server-side triggers ignored here. */
const UI_TRIGGERS: BusinessRuleTrigger[] = ['ON_FORM_LOAD', 'ON_FORM_CHANGE'];

export function computeFieldUiOverrides(
  rules: BusinessRuleDto[],
  values: Record<string, unknown>
): FieldUiOverrides {
  const applicable = rules
    .filter((r) => r.active && UI_TRIGGERS.includes(r.trigger))
    .sort((a, b) => a.priority - b.priority || a.id.localeCompare(b.id));

  const out: FieldUiOverrides = {};

  for (const rule of applicable) {
    const cond = rule.condition as RuleCondition;
    try {
      if (!evalCondition(cond as RuleCondition, values)) continue;
    } catch {
      continue;
    }
    const actions = [...rule.actions].filter((a) => a.applyUi).sort((a, b) => a.priority - b.priority || a.id.localeCompare(b.id));
    for (const a of actions) {
      const p = a.payload || {};
      const target = typeof p.targetField === 'string' ? p.targetField : '';
      if (!target) continue;
      const cur = out[target] ?? {};
      switch (a.actionType) {
        case 'UI_SET_FIELD_VISIBILITY':
          if (typeof p.visible === 'boolean') {
            cur.hidden = !p.visible;
          }
          break;
        case 'UI_SET_FIELD_READ_ONLY':
          if (typeof p.readOnly === 'boolean') {
            cur.readOnly = p.readOnly;
          }
          break;
        case 'UI_SET_FIELD_REQUIRED':
          if (typeof p.required === 'boolean') {
            cur.required = p.required;
          }
          break;
        default:
          break;
      }
      out[target] = cur;
    }
  }
  return out;
}

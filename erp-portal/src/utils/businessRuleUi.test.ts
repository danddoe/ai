import { describe, expect, it } from 'vitest';
import { computeFieldUiOverrides, evalCondition, type BusinessRuleDto } from './businessRuleUi';

// Mirror entity-builder golden: condition_and_country_us_amount_gte.json
const GOLDEN_AND = {
  op: 'and' as const,
  children: [
    { op: 'cmp' as const, field: 'country', operator: 'eq', value: 'US' },
    { op: 'cmp' as const, field: 'amount', operator: 'gte', value: 1000 },
  ],
};

describe('businessRuleUi', () => {
  it('evaluates golden AND condition like Java RuleConditionEvaluator', () => {
    expect(evalCondition(GOLDEN_AND, { country: 'US', amount: 1500 })).toBe(true);
    expect(evalCondition(GOLDEN_AND, { country: 'US', amount: 100 })).toBe(false);
  });

  it('computeFieldUiOverrides applies visibility', () => {
    const rules: BusinessRuleDto[] = [
      {
        id: '1',
        tenantId: 't',
        entityId: 'e',
        formLayoutId: null,
        name: 'hide',
        description: null,
        priority: 0,
        trigger: 'ON_FORM_CHANGE',
        condition: { op: 'cmp', field: 'x', operator: 'eq', value: 1 },
        active: true,
        actions: [
          {
            id: 'a',
            priority: 0,
            actionType: 'UI_SET_FIELD_VISIBILITY',
            payload: { targetField: 'secret', visible: false },
            applyUi: true,
            applyServer: false,
          },
        ],
        createdAt: '',
        updatedAt: '',
      },
    ];
    expect(computeFieldUiOverrides(rules, { x: 1 }).secret?.hidden).toBe(true);
    expect(computeFieldUiOverrides(rules, { x: 2 }).secret).toBeUndefined();
  });
});

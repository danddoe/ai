import { describe, expect, it } from 'vitest';
import type { EntityDto, EntityFieldDto, RecordDto } from '../api/schemas';
import {
  buildRecordListSummaryDisplay,
  buildReferenceRecordLabel,
  collectReferenceResolutionTasks,
  dedupeSlugsPreserveOrder,
  entitiesBySlugLower,
  LIST_SUMMARY_SEPARATOR,
} from './referenceRecordDisplayLabel';

describe('dedupeSlugsPreserveOrder', () => {
  it('dedupes case-insensitively preserving first occurrence casing', () => {
    expect(dedupeSlugsPreserveOrder(['A', 'a', 'b'])).toEqual(['A', 'b']);
  });

  it('trims and skips empty', () => {
    expect(dedupeSlugsPreserveOrder([' x ', '', 'y'])).toEqual(['x', 'y']);
  });
});

describe('buildReferenceRecordLabel', () => {
  const rec: RecordDto = {
    id: 'r1',
    tenantId: 't',
    entityId: 'e',
    externalId: null,
    businessDocumentNumber: null,
    createdBy: null,
    updatedBy: null,
    status: 'ACTIVE',
    values: { code: 'ACME', name: 'Acme Corp' },
    links: [],
    createdAt: '',
    updatedAt: '',
  };

  it('uses default display slug when no lookup columns', () => {
    expect(buildReferenceRecordLabel(rec, 'name', [])).toBe('Acme Corp');
  });

  it('joins configured slugs with separator', () => {
    expect(buildReferenceRecordLabel(rec, 'name', ['code', 'name'])).toBe(`ACME${LIST_SUMMARY_SEPARATOR}Acme Corp`);
  });

  it('falls back to first non-empty value when default slug empty', () => {
    const r2: RecordDto = { ...rec, values: { code: 'X', note: '' } };
    expect(buildReferenceRecordLabel(r2, 'missing', [])).toBe('X');
  });
});

describe('buildRecordListSummaryDisplay', () => {
  const row: RecordDto = {
    id: 'row1',
    tenantId: 't',
    entityId: 'e',
    externalId: null,
    businessDocumentNumber: null,
    createdBy: null,
    updatedBy: null,
    status: 'ACTIVE',
    values: { title: 'Hello', ref: '550e8400-e29b-41d4-a716-446655440000' },
    links: [],
    createdAt: '',
    updatedAt: '',
  };

  const refField = {
    slug: 'ref',
    fieldType: 'reference',
  } as EntityFieldDto;

  const titleField = {
    slug: 'title',
    fieldType: 'string',
  } as EntityFieldDto;

  it('concatenates scalar and resolved reference labels', () => {
    const map = {
      'ref::550e8400-e29b-41d4-a716-446655440000': 'Linked name',
    };
    const s = buildRecordListSummaryDisplay(row, [titleField, refField], map);
    expect(s).toBe(`Hello${LIST_SUMMARY_SEPARATOR}Linked name`);
  });

  it('uses em dash when every part empty', () => {
    const emptyRow: RecordDto = { ...row, values: {} as Record<string, unknown> };
    expect(buildRecordListSummaryDisplay(emptyRow, [titleField], {})).toBe('—');
  });
});

describe('collectReferenceResolutionTasks', () => {
  it('returns tasks only for reference fields in slug set with known entity', () => {
    const fields = [
      {
        slug: 'r',
        fieldType: 'reference',
        config: { targetEntitySlug: 'contact' },
      },
      { slug: 'n', fieldType: 'string' },
    ] as EntityFieldDto[];
    const entities: EntityDto[] = [
      {
        id: 'ent-cont',
        slug: 'contact',
        name: 'C',
        tenantId: 't',
        status: 'ACTIVE',
        createdAt: '',
        updatedAt: '',
      },
    ];
    const bySlug = entitiesBySlugLower(entities);
    const tasks = collectReferenceResolutionTasks(fields, new Set(['r', 'n']), bySlug);
    expect(tasks).toHaveLength(1);
    expect(tasks[0].targetEntityId).toBe('ent-cont');
  });
});

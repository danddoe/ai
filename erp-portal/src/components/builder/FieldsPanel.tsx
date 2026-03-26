import { useMemo, useState } from 'react';
import { Button, Group, Select, Text, TextInput, UnstyledButton } from '@mantine/core';
import type { EntityFieldDto } from '../../api/schemas';
import { useAuth } from '../../auth/AuthProvider';
import type { LayoutV2 } from '../../types/formLayout';
import { fieldOnForm } from '../../utils/layoutV2';

type AddTarget = { regionIndex: number; rowIndex: number; columnIndex: number } | null;

const FIELDS_LIST_SORT_KEY = 'erp.formBuilder.fieldsListSort';

type FieldsListSortBy = 'schema' | 'name' | 'slug';

function readStoredSortBy(): FieldsListSortBy {
  try {
    const v = localStorage.getItem(FIELDS_LIST_SORT_KEY);
    if (v === 'schema' || v === 'name' || v === 'slug') return v;
  } catch {
    /* ignore */
  }
  return 'name';
}

function sortFieldsList(list: EntityFieldDto[], sortBy: FieldsListSortBy): EntityFieldDto[] {
  if (sortBy === 'schema') return list;
  const copy = [...list];
  if (sortBy === 'name') {
    copy.sort((a, b) => a.name.localeCompare(b.name, undefined, { sensitivity: 'base' }));
  } else {
    copy.sort((a, b) => a.slug.localeCompare(b.slug));
  }
  return copy;
}

type Props = {
  fields: EntityFieldDto[];
  layout: LayoutV2;
  search: string;
  onSearchChange: (s: string) => void;
  filter: 'all' | 'on' | 'off';
  onFilterChange: (f: 'all' | 'on' | 'off') => void;
  addTarget: AddTarget;
  onClearAddTarget: () => void;
  onPickField: (field: EntityFieldDto) => void;
  onOpenCreateField: () => void;
  onOpenEditField: (field: EntityFieldDto) => void;
  /** When set, controls create/edit field actions (e.g. false for catalog entities without platform schema write). */
  fieldDefinitionsWritable?: boolean;
};

export function FieldsPanel({
  fields,
  layout,
  search,
  onSearchChange,
  filter,
  onFilterChange,
  addTarget,
  onClearAddTarget,
  onPickField,
  onOpenCreateField,
  onOpenEditField,
  fieldDefinitionsWritable,
}: Props) {
  const { canSchemaWrite } = useAuth();
  const canMutateFields = fieldDefinitionsWritable ?? canSchemaWrite;
  const [sortBy, setSortBy] = useState<FieldsListSortBy>(readStoredSortBy);
  const q = search.trim().toLowerCase();
  const filtered = useMemo(() => {
    const ordered = sortFieldsList(fields, sortBy);
    return ordered.filter((f) => {
      if (q && !f.name.toLowerCase().includes(q) && !f.slug.toLowerCase().includes(q)) return false;
      const on = fieldOnForm(layout, f.id, f.slug);
      if (filter === 'on' && !on) return false;
      if (filter === 'off' && on) return false;
      return true;
    });
  }, [fields, sortBy, q, filter, layout]);

  return (
    <aside className="builder-panel">
      <div className="builder-panel-header">
        <h2>Data dictionary</h2>
        {canMutateFields && (
          <Button variant="default" size="xs" onClick={onOpenCreateField}>
            + New field
          </Button>
        )}
      </div>
      {addTarget && (
        <div className="builder-hint" role="status">
          Choose a field to place in the selected column (or use <strong>+ Save</strong> / <strong>+ Cancel</strong> /{' '}
          <strong>+ Link</strong> in structure), or{' '}
          <UnstyledButton type="button" c="blue" td="underline" fz="inherit" onClick={onClearAddTarget}>
            cancel
          </UnstyledButton>
          .
        </div>
      )}
      <TextInput
        className="builder-search"
        placeholder="Search fields…"
        value={search}
        onChange={(e) => onSearchChange(e.target.value)}
        aria-label="Search fields"
        size="xs"
      />
      <Group wrap="nowrap" gap="xs" align="center" className="builder-filter-row">
        <Text span size="xs" c="dimmed" className="builder-filter-label">
          Show
        </Text>
        <Select
          size="xs"
          w={130}
          data={[
            { value: 'all', label: 'All' },
            { value: 'on', label: 'On form' },
            { value: 'off', label: 'Not on form' },
          ]}
          value={filter}
          onChange={(v) => v && onFilterChange(v as 'all' | 'on' | 'off')}
        />
      </Group>
      <Group wrap="nowrap" gap="xs" align="center" className="builder-filter-row">
        <Text span size="xs" c="dimmed" className="builder-filter-label">
          Sort by
        </Text>
        <Select
          size="xs"
          w={150}
          aria-label="Sort fields list"
          data={[
            { value: 'name', label: 'Name (A–Z)' },
            { value: 'slug', label: 'Slug (A–Z)' },
            { value: 'schema', label: 'Schema order' },
          ]}
          value={sortBy}
          onChange={(v) => {
            if (!v) return;
            const next = v as FieldsListSortBy;
            setSortBy(next);
            try {
              localStorage.setItem(FIELDS_LIST_SORT_KEY, next);
            } catch {
              /* ignore */
            }
          }}
        />
      </Group>
      {fields.length === 0 && canMutateFields && (
        <p className="builder-muted" style={{ marginBottom: 8, fontSize: '0.875rem' }}>
          No schema fields yet. Use <strong>New field</strong> to add one, or create a field from an unresolved
          placement in the structure (select the placement, then Properties).
        </p>
      )}
      <ul className="builder-field-list">
        {filtered.map((f) => {
          const on = fieldOnForm(layout, f.id, f.slug);
          return (
            <li key={f.id}>
              <div className="builder-field-row">
                <button
                  type="button"
                  className={`builder-field-btn${addTarget ? ' focusable' : ''}`}
                  disabled={!addTarget}
                  onClick={() => addTarget && onPickField(f)}
                  title={addTarget ? `Add ${f.name} to column` : 'Select a column (Add field) first'}
                >
                  <span className="builder-field-name">{f.name}</span>
                  <span className="builder-field-meta">
                    <code>{f.slug}</code>
                    <span className={`pill ${on ? 'pill-on' : 'pill-off'}`}>{on ? 'on form' : 'off'}</span>
                  </span>
                </button>
                {canMutateFields && (
                  <Button
                    type="button"
                    variant="default"
                    size="xs"
                    onClick={() => onOpenEditField(f)}
                    aria-label={`Edit field ${f.name}`}
                  >
                    Edit
                  </Button>
                )}
              </div>
            </li>
          );
        })}
      </ul>
      {filtered.length === 0 && <p className="builder-muted">No fields match.</p>}
    </aside>
  );
}

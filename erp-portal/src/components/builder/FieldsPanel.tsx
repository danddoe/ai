import { useMemo, useState } from 'react';
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
}: Props) {
  const { canSchemaWrite } = useAuth();
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
        {canSchemaWrite && (
          <button type="button" className="btn btn-sm btn-secondary" onClick={onOpenCreateField}>
            + New field
          </button>
        )}
      </div>
      {addTarget && (
        <div className="builder-hint" role="status">
          Choose a field to place in the selected column (or use <strong>+ Save</strong> / <strong>+ Cancel</strong> /{' '}
          <strong>+ Link</strong> in structure), or{' '}
          <button type="button" className="link-btn" onClick={onClearAddTarget}>
            cancel
          </button>
          .
        </div>
      )}
      <input
        className="input builder-search"
        placeholder="Search fields…"
        value={search}
        onChange={(e) => onSearchChange(e.target.value)}
        aria-label="Search fields"
      />
      <div className="builder-filter-row">
        <span className="builder-filter-label">Show</span>
        <select className="input input-sm" value={filter} onChange={(e) => onFilterChange(e.target.value as 'all' | 'on' | 'off')}>
          <option value="all">All</option>
          <option value="on">On form</option>
          <option value="off">Not on form</option>
        </select>
      </div>
      <div className="builder-filter-row">
        <span className="builder-filter-label">Sort by</span>
        <select
          className="input input-sm"
          value={sortBy}
          onChange={(e) => {
            const v = e.target.value as FieldsListSortBy;
            setSortBy(v);
            try {
              localStorage.setItem(FIELDS_LIST_SORT_KEY, v);
            } catch {
              /* ignore */
            }
          }}
          aria-label="Sort fields list"
        >
          <option value="name">Name (A–Z)</option>
          <option value="slug">Slug (A–Z)</option>
          <option value="schema">Schema order</option>
        </select>
      </div>
      {fields.length === 0 && canSchemaWrite && (
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
                {canSchemaWrite && (
                  <button
                    type="button"
                    className="btn btn-sm btn-secondary"
                    onClick={() => onOpenEditField(f)}
                    aria-label={`Edit field ${f.name}`}
                  >
                    Edit
                  </button>
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

import type { EntityFieldDto } from '../../api/schemas';
import { useAuth } from '../../auth/AuthProvider';
import {
  RECORD_LIST_ROW_ID_SLUG,
  type RecordListColumnDefinition,
} from '../../utils/recordListViewDefinition';

type Props = {
  fields: EntityFieldDto[];
  columns: RecordListColumnDefinition[];
  search: string;
  onSearchChange: (s: string) => void;
  filter: 'all' | 'on' | 'off';
  onFilterChange: (f: 'all' | 'on' | 'off') => void;
  onAddColumn: (slug: string) => void;
  onOpenCreateField: () => void;
  onOpenEditField: (field: EntityFieldDto) => void;
  schemaWritable: boolean;
};

function fieldOnList(columns: RecordListColumnDefinition[], slug: string): boolean {
  return columns.some((c) => c.fieldSlug === slug);
}

export function ListDesignerFieldsPanel({
  fields,
  columns,
  search,
  onSearchChange,
  filter,
  onFilterChange,
  onAddColumn,
  onOpenCreateField,
  onOpenEditField,
  schemaWritable,
}: Props) {
  const { canSchemaWrite } = useAuth();
  const q = search.trim().toLowerCase();
  const filtered = fields.filter((f) => {
    if (q && !f.name.toLowerCase().includes(q) && !f.slug.toLowerCase().includes(q)) return false;
    const on = fieldOnList(columns, f.slug);
    if (filter === 'on' && !on) return false;
    if (filter === 'off' && on) return false;
    return true;
  });

  return (
    <aside className="builder-panel">
      <div className="builder-panel-header">
        <h2>Data dictionary</h2>
        {canSchemaWrite && schemaWritable && (
          <button type="button" className="btn btn-sm btn-secondary" onClick={onOpenCreateField}>
            + New field
          </button>
        )}
      </div>
      <p className="builder-muted" style={{ marginBottom: 8, fontSize: '0.8125rem' }}>
        Click a field to add it as a table column. Reorder columns in the structure panel.
      </p>
      <input
        className="input builder-search"
        placeholder="Search fields…"
        value={search}
        onChange={(e) => onSearchChange(e.target.value)}
        aria-label="Search fields"
      />
      <div className="builder-filter-row">
        <span className="builder-filter-label">Show</span>
        <select
          className="input input-sm"
          value={filter}
          onChange={(e) => onFilterChange(e.target.value as 'all' | 'on' | 'off')}
        >
          <option value="all">All</option>
          <option value="on">On list</option>
          <option value="off">Not on list</option>
        </select>
      </div>
      {fields.length === 0 && canSchemaWrite && (
        <p className="builder-muted" style={{ marginBottom: 8, fontSize: '0.875rem' }}>
          No schema fields yet. Use <strong>New field</strong> to add one.
        </p>
      )}
      <ul className="builder-field-list">
        {filtered.map((f) => {
          const on = fieldOnList(columns, f.slug);
          const isRowIdSlug = f.slug.trim().toLowerCase() === RECORD_LIST_ROW_ID_SLUG;
          return (
            <li key={f.id}>
              <div className="builder-field-row">
                <button
                  type="button"
                  className={`builder-field-btn${schemaWritable && !isRowIdSlug ? ' focusable' : ''}`}
                  disabled={!schemaWritable || isRowIdSlug}
                  onClick={() => schemaWritable && !isRowIdSlug && onAddColumn(f.slug)}
                  title={
                    isRowIdSlug
                      ? 'Use “Show record ID column” above to show or hide the UUID on Records; it is not a list column.'
                      : schemaWritable
                        ? `Add ${f.name} as column`
                        : 'Schema write required'
                  }
                >
                  <span className="builder-field-name">{f.name}</span>
                  <span className="builder-field-meta">
                    <code>{f.slug}</code>
                    <span className={`pill ${on ? 'pill-on' : 'pill-off'}`}>{on ? 'on list' : 'off'}</span>
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

import type { EntityFieldDto } from '../../api/schemas';
import type { LayoutActionType, LayoutV2, RegionRole, StructureSelection } from '../../types/formLayout';
import { countItemsInRegion, countItemsInRow, isActionItem, isSafeActionHref, resolveLayoutItemField } from '../../utils/layoutV2';
import * as M from '../../utils/layoutMutations';

type Props = {
  layout: LayoutV2;
  fields: EntityFieldDto[];
  onChange: (next: LayoutV2) => void;
  selection: StructureSelection | null;
  onSelect: (s: StructureSelection | null) => void;
  onRequestAddField: (target: { regionIndex: number; rowIndex: number; columnIndex: number }) => void;
  /** Insert Save / Cancel / Link action items (record form + validated URLs). */
  onAddActionItem?: (
    target: { regionIndex: number; rowIndex: number; columnIndex: number },
    action: LayoutActionType,
    linkHref?: string
  ) => void;
  schemaWritable?: boolean;
};

function roleLabel(role: RegionRole): string {
  return role === 'header' ? 'Header' : role === 'tab' ? 'Tab' : 'Detail';
}

export function StructurePanel({
  layout,
  fields,
  onChange,
  selection,
  onSelect,
  onRequestAddField,
  onAddActionItem,
  schemaWritable = true,
}: Props) {
  function selKey(s: StructureSelection | null): string {
    if (!s) return '';
    if (s.kind === 'region') return `r${s.regionIndex}`;
    if (s.kind === 'row') return `r${s.regionIndex}-row${s.rowIndex}`;
    if (s.kind === 'column') return `r${s.regionIndex}-row${s.rowIndex}-c${s.columnIndex}`;
    return `r${s.regionIndex}-row${s.rowIndex}-c${s.columnIndex}-i${s.itemIndex}`;
  }

  const sk = selKey(selection);

  return (
    <section className="builder-panel builder-panel-grow">
      <div className="builder-panel-header">
        <h2>Form structure</h2>
        <span className="builder-muted" style={{ fontSize: '0.75rem' }}>
          Add fields or action buttons (Save / Cancel / Link). Reorder with Row ↑↓ and Col ↑↓.
        </span>
      </div>
      <div className="builder-structure">
        {layout.regions.map((region, ri) => (
          <div
            key={region.id}
            className={`builder-region${sk === `r${ri}` ? ' builder-selected' : ''}`}
            onClick={() => onSelect({ kind: 'region', regionIndex: ri })}
            onKeyDown={(e) => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                onSelect({ kind: 'region', regionIndex: ri });
              }
            }}
            role="button"
            tabIndex={0}
          >
            <div className="builder-region-head">
              <span className="builder-role">{roleLabel(region.role)}</span>
              <input
                className="input input-inline"
                value={region.title}
                onClick={(e) => e.stopPropagation()}
                onChange={(e) => onChange(M.setRegionTitle(layout, ri, e.target.value))}
                readOnly={!schemaWritable}
                aria-label={`Region ${ri + 1} title`}
              />
              <div className="builder-toolbar">
                <button
                  type="button"
                  className="btn btn-xs btn-secondary focusable"
                  disabled={!schemaWritable}
                  onClick={(e) => {
                    e.stopPropagation();
                    onChange(M.moveRegion(layout, ri, -1));
                  }}
                >
                  Region ↑
                </button>
                <button
                  type="button"
                  className="btn btn-xs btn-secondary focusable"
                  disabled={!schemaWritable}
                  onClick={(e) => {
                    e.stopPropagation();
                    onChange(M.moveRegion(layout, ri, 1));
                  }}
                >
                  Region ↓
                </button>
                <button
                  type="button"
                  className="btn btn-xs btn-secondary focusable"
                  disabled={!schemaWritable}
                  onClick={(e) => {
                    e.stopPropagation();
                    onChange(M.addRow(layout, ri));
                  }}
                >
                  + Row
                </button>
                <button
                  type="button"
                  className="btn btn-xs btn-danger focusable"
                  disabled={!schemaWritable}
                  onClick={(e) => {
                    e.stopPropagation();
                    const n = countItemsInRegion(region);
                    if (n > 0 && !window.confirm(`Remove region and ${n} field placement(s)?`)) return;
                    onChange(M.removeRegion(layout, ri));
                    onSelect(null);
                  }}
                >
                  Remove region
                </button>
              </div>
            </div>
            {region.rows.map((row, rowIdx) => (
              <div
                key={row.id}
                className={`builder-row${sk === `r${ri}-row${rowIdx}` ? ' builder-selected' : ''}`}
                onClick={(e) => {
                  e.stopPropagation();
                  onSelect({ kind: 'row', regionIndex: ri, rowIndex: rowIdx });
                }}
              >
                <div className="builder-row-head">
                  <span className="builder-muted">Row {rowIdx + 1}</span>
                  <div className="builder-toolbar">
                    <button
                      type="button"
                      className="btn btn-xs btn-secondary focusable"
                      disabled={!schemaWritable}
                      onClick={(e) => {
                        e.stopPropagation();
                        onChange(M.moveRow(layout, ri, rowIdx, -1));
                      }}
                    >
                      Row ↑
                    </button>
                    <button
                      type="button"
                      className="btn btn-xs btn-secondary focusable"
                      disabled={!schemaWritable}
                      onClick={(e) => {
                        e.stopPropagation();
                        onChange(M.moveRow(layout, ri, rowIdx, 1));
                      }}
                    >
                      Row ↓
                    </button>
                    <button
                      type="button"
                      className="btn btn-xs btn-secondary focusable"
                      disabled={!schemaWritable}
                      onClick={(e) => {
                        e.stopPropagation();
                        onChange(M.addColumn(layout, ri, rowIdx));
                      }}
                    >
                      + Column
                    </button>
                    <button
                      type="button"
                      className="btn btn-xs btn-danger focusable"
                      disabled={!schemaWritable}
                      onClick={(e) => {
                        e.stopPropagation();
                        const n = countItemsInRow(row);
                        if (n > 0 && !window.confirm(`Remove row and ${n} item(s)?`)) return;
                        onChange(M.removeRow(layout, ri, rowIdx));
                        onSelect(null);
                      }}
                    >
                      Remove row
                    </button>
                  </div>
                </div>
                <div className="builder-columns">
                  {row.columns.map((col, colIdx) => (
                    <div
                      key={col.id}
                      className={`builder-column${sk === `r${ri}-row${rowIdx}-c${colIdx}` ? ' builder-selected' : ''}`}
                      style={{ flex: col.span }}
                      onClick={(e) => {
                        e.stopPropagation();
                        onSelect({ kind: 'column', regionIndex: ri, rowIndex: rowIdx, columnIndex: colIdx });
                      }}
                    >
                      <div className="builder-column-head">
                        <label className="builder-span-label">
                          Span
                          <input
                            type="number"
                            min={1}
                            max={12}
                            className="input input-xs"
                            value={col.span}
                            onClick={(e) => e.stopPropagation()}
                            onChange={(e) =>
                              onChange(
                                M.setColumnSpan(layout, ri, rowIdx, colIdx, Number(e.target.value) || 1)
                              )
                            }
                            disabled={!schemaWritable}
                          />
                        </label>
                        <div className="builder-toolbar">
                          <button
                            type="button"
                            className="btn btn-xs btn-secondary focusable"
                            disabled={!schemaWritable}
                            onClick={(e) => {
                              e.stopPropagation();
                              onChange(M.moveColumn(layout, ri, rowIdx, colIdx, -1));
                            }}
                          >
                            Col ↑
                          </button>
                          <button
                            type="button"
                            className="btn btn-xs btn-secondary focusable"
                            disabled={!schemaWritable}
                            onClick={(e) => {
                              e.stopPropagation();
                              onChange(M.moveColumn(layout, ri, rowIdx, colIdx, 1));
                            }}
                          >
                            Col ↓
                          </button>
                          <button
                            type="button"
                            className="btn btn-xs btn-primary focusable"
                            disabled={!schemaWritable}
                            onClick={(e) => {
                              e.stopPropagation();
                              onRequestAddField({ regionIndex: ri, rowIndex: rowIdx, columnIndex: colIdx });
                            }}
                          >
                            Add field
                          </button>
                          {schemaWritable && onAddActionItem ? (
                            <>
                              <button
                                type="button"
                                className="btn btn-xs btn-secondary focusable"
                                onClick={(e) => {
                                  e.stopPropagation();
                                  onAddActionItem(
                                    { regionIndex: ri, rowIndex: rowIdx, columnIndex: colIdx },
                                    'save'
                                  );
                                }}
                              >
                                + Save
                              </button>
                              <button
                                type="button"
                                className="btn btn-xs btn-secondary focusable"
                                onClick={(e) => {
                                  e.stopPropagation();
                                  onAddActionItem(
                                    { regionIndex: ri, rowIndex: rowIdx, columnIndex: colIdx },
                                    'cancel'
                                  );
                                }}
                              >
                                + Cancel
                              </button>
                              <button
                                type="button"
                                className="btn btn-xs btn-secondary focusable"
                                onClick={(e) => {
                                  e.stopPropagation();
                                  const href = window.prompt('URL (https://… or /path)', 'https://');
                                  if (href === null) return;
                                  const t = href.trim();
                                  if (!t) return;
                                  if (!isSafeActionHref(t)) {
                                    window.alert('Use https://… or a path starting with / (not //).');
                                    return;
                                  }
                                  onAddActionItem(
                                    { regionIndex: ri, rowIndex: rowIdx, columnIndex: colIdx },
                                    'link',
                                    t
                                  );
                                }}
                              >
                                + Link
                              </button>
                            </>
                          ) : null}
                          <button
                            type="button"
                            className="btn btn-xs btn-danger focusable"
                            disabled={!schemaWritable}
                            onClick={(e) => {
                              e.stopPropagation();
                              if (col.items.length > 0 && !window.confirm('Remove column and its items?')) return;
                              onChange(M.removeColumn(layout, ri, rowIdx, colIdx));
                              onSelect(null);
                            }}
                          >
                            Remove col
                          </button>
                        </div>
                      </div>
                      <ul className="builder-items">
                        {col.items.map((item, itemIdx) => {
                          const resolved = resolveLayoutItemField(item, fields);
                          const isBtn = isActionItem(item);
                          const title = isBtn
                            ? item.label
                            : item.presentation?.label?.trim() ||
                              resolved?.labelOverride?.trim() ||
                              resolved?.name ||
                              item.fieldSlug ||
                              item.fieldId ||
                              'Field';
                          return (
                          <li key={item.id}>
                            <button
                              type="button"
                              className={`builder-item-btn${
                                sk === `r${ri}-row${rowIdx}-c${colIdx}-i${itemIdx}` ? ' builder-selected' : ''
                              } focusable`}
                              onClick={(e) => {
                                e.stopPropagation();
                                onSelect({
                                  kind: 'item',
                                  regionIndex: ri,
                                  rowIndex: rowIdx,
                                  columnIndex: colIdx,
                                  itemIndex: itemIdx,
                                });
                              }}
                            >
                              <span className="builder-item-title">{title}</span>
                              {isBtn ? (
                                <span className="pill pill-on" title="Layout action button">
                                  {item.action === 'link' ? 'link' : item.action}
                                </span>
                              ) : !resolved ? (
                                <span className="pill pill-warn" title="No matching schema field — use Properties to create or bind">
                                  missing
                                </span>
                              ) : null}
                              <span className="builder-muted">
                                {isBtn ? (
                                  item.action === 'link' && item.href ? (
                                    <code style={{ wordBreak: 'break-all' }}>{item.href}</code>
                                  ) : (
                                    <span>action</span>
                                  )
                                ) : (
                                  <code>{resolved?.slug ?? item.fieldSlug ?? '—'}</code>
                                )}
                              </span>
                            </button>
                            <div className="builder-item-actions">
                              <button
                                type="button"
                                className="btn btn-xs btn-secondary focusable"
                                disabled={!schemaWritable}
                                onClick={(e) => {
                                  e.stopPropagation();
                                  onChange(M.moveItem(layout, ri, rowIdx, colIdx, itemIdx, -1));
                                }}
                              >
                                ↑
                              </button>
                              <button
                                type="button"
                                className="btn btn-xs btn-secondary focusable"
                                disabled={!schemaWritable}
                                onClick={(e) => {
                                  e.stopPropagation();
                                  onChange(M.moveItem(layout, ri, rowIdx, colIdx, itemIdx, 1));
                                }}
                              >
                                ↓
                              </button>
                              <button
                                type="button"
                                className="btn btn-xs btn-danger focusable"
                                disabled={!schemaWritable}
                                onClick={(e) => {
                                  e.stopPropagation();
                                  onChange(M.removeItem(layout, ri, rowIdx, colIdx, itemIdx));
                                  onSelect(null);
                                }}
                              >
                                Remove
                              </button>
                            </div>
                          </li>
                          );
                        })}
                      </ul>
                    </div>
                  ))}
                </div>
              </div>
            ))}
          </div>
        ))}
      </div>
      <div className="builder-region-add">
        <span className="builder-muted">Add region:</span>
        <button
          type="button"
          className="btn btn-sm btn-secondary focusable"
          disabled={!schemaWritable}
          onClick={() => onChange(M.addRegion(layout, 'header'))}
        >
          + Header
        </button>
        <button
          type="button"
          className="btn btn-sm btn-secondary focusable"
          disabled={!schemaWritable}
          onClick={() => onChange(M.addRegion(layout, 'tab'))}
        >
          + Tab
        </button>
        <button
          type="button"
          className="btn btn-sm btn-secondary focusable"
          disabled={!schemaWritable}
          onClick={() => onChange(M.addRegion(layout, 'detail'))}
        >
          + Detail
        </button>
      </div>
    </section>
  );
}

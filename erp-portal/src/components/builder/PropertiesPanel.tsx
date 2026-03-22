import type { EntityFieldDto } from '../../api/schemas';
import type { LayoutActionType, LayoutItemAction, LayoutV2, StructureSelection } from '../../types/formLayout';
import * as M from '../../utils/layoutMutations';
import { isActionItem, isSafeActionHref, resolveLayoutItemField } from '../../utils/layoutV2';

function titleCaseFromSlug(slug: string): string {
  const s = slug.trim();
  if (!s) return '';
  return s
    .split(/[-_]/g)
    .filter(Boolean)
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase())
    .join(' ');
}

type Props = {
  layout: LayoutV2;
  selection: StructureSelection | null;
  fields: EntityFieldDto[];
  schemaWritable: boolean;
  onChange: (next: LayoutV2) => void;
  onClearSelection: () => void;
  onOpenCreateField: (opts?: {
    suggestedName?: string;
    suggestedSlug?: string;
    bindSelection?: { regionIndex: number; rowIndex: number; columnIndex: number; itemIndex: number };
  }) => void;
  onOpenEditField: (field: EntityFieldDto) => void;
};

export function PropertiesPanel({
  layout,
  selection,
  fields,
  schemaWritable,
  onChange,
  onClearSelection,
  onOpenCreateField,
  onOpenEditField,
}: Props) {
  if (!selection || selection.kind !== 'item') {
    return (
      <aside className="builder-panel">
        <div className="builder-panel-header">
          <h2>Properties</h2>
        </div>
        <p className="builder-muted">Select a field or button on the form to edit properties.</p>
      </aside>
    );
  }

  const { regionIndex, rowIndex, columnIndex, itemIndex } = selection;
  const item = layout.regions[regionIndex]?.rows[rowIndex]?.columns[columnIndex]?.items[itemIndex];
  if (!item) {
    return (
      <aside className="builder-panel">
        <p className="builder-muted">Selection no longer valid.</p>
        <button type="button" className="btn btn-sm btn-secondary" onClick={onClearSelection}>
          Clear
        </button>
      </aside>
    );
  }

  if (isActionItem(item)) {
    function patchAction(
      partial: Partial<Pick<LayoutItemAction, 'action' | 'label' | 'href' | 'openInNewTab' | 'variant'>>
    ) {
      const next: Partial<Pick<LayoutItemAction, 'action' | 'label' | 'href' | 'openInNewTab' | 'variant'>> = {
        ...partial,
      };
      if (partial.action && partial.action !== 'link') {
        next.href = undefined;
      }
      onChange(M.updateLayoutActionItem(layout, regionIndex, rowIndex, columnIndex, itemIndex, next));
    }
    const hrefBad = item.action === 'link' && !isSafeActionHref(item.href ?? '');
    return (
      <aside className="builder-panel">
        <div className="builder-panel-header">
          <h2>Button</h2>
        </div>
        <p className="builder-muted" style={{ fontSize: '0.8125rem', marginBottom: 12 }}>
          Save and Cancel use the record form&apos;s save and back-to-list actions. Links must use{' '}
          <code>https://</code> or an in-app path starting with <code>/</code> (not <code>//</code>).
        </p>
        <label className="field-label">
          Action
          <select
            className="input"
            disabled={!schemaWritable}
            value={item.action}
            onChange={(e) => {
              const next = e.target.value as LayoutActionType;
              patchAction({ action: next });
            }}
          >
            <option value="save">Save record</option>
            <option value="cancel">Cancel (back to list)</option>
            <option value="link">Open link</option>
          </select>
        </label>
        <label className="field-label">
          Label
          <input
            className="input"
            disabled={!schemaWritable}
            value={item.label}
            onChange={(e) => patchAction({ label: e.target.value })}
          />
        </label>
        {item.action === 'link' ? (
          <>
            <label className="field-label">
              URL or path
              <input
                className="input"
                disabled={!schemaWritable}
                value={item.href ?? ''}
                placeholder="https://… or /entities/…"
                onChange={(e) => patchAction({ href: e.target.value })}
              />
            </label>
            {hrefBad ? (
              <p className="text-error" style={{ fontSize: '0.8125rem' }}>
                Invalid URL. Use https:// or a path starting with / .
              </p>
            ) : null}
            <label className="field-label row">
              <input
                type="checkbox"
                disabled={!schemaWritable}
                checked={Boolean(item.openInNewTab)}
                onChange={(e) => patchAction({ openInNewTab: e.target.checked })}
              />
              Open in new tab
            </label>
          </>
        ) : null}
        <label className="field-label">
          Style
          <select
            className="input"
            disabled={!schemaWritable}
            value={item.variant ?? ''}
            onChange={(e) => {
              const v = e.target.value;
              patchAction({ variant: v === '' ? undefined : (v as LayoutItemAction['variant']) });
            }}
          >
            <option value="">Default (by action)</option>
            <option value="primary">Primary</option>
            <option value="secondary">Secondary</option>
            <option value="link">Link style</option>
          </select>
        </label>
        {schemaWritable ? (
          <button
            type="button"
            className="btn btn-danger btn-sm"
            style={{ marginTop: 16, width: '100%' }}
            onClick={() => {
              if (!window.confirm('Remove this button from the form?')) return;
              onChange(M.removeItem(layout, regionIndex, rowIndex, columnIndex, itemIndex));
              onClearSelection();
            }}
          >
            Remove button
          </button>
        ) : null}
      </aside>
    );
  }

  const p = item.presentation;

  function patchPresentation(partial: Partial<typeof p>) {
    onChange(
      M.updateItemPresentation(layout, regionIndex, rowIndex, columnIndex, itemIndex, {
        ...p,
        ...partial,
      })
    );
  }

  const resolvedField = resolveLayoutItemField(item, fields);
  const unresolved = !resolvedField;
  const bindSelection = { regionIndex, rowIndex, columnIndex, itemIndex };
  const bindOptions = fields.filter((f) => f.id !== resolvedField?.id);
  const suggestedSlug = (item.fieldSlug ?? '').trim();
  const suggestedName =
    (item.presentation?.label ?? '').trim() || (suggestedSlug ? titleCaseFromSlug(suggestedSlug) : '');

  return (
    <aside className="builder-panel">
      <div className="builder-panel-header">
        <h2>Presentation</h2>
      </div>
      {resolvedField && (
        <div style={{ marginBottom: 12, paddingBottom: 12, borderBottom: '1px solid #e4e4e7' }}>
          <div className="builder-muted" style={{ fontSize: '0.75rem', marginBottom: 6 }}>
            Schema field
          </div>
          <div style={{ fontSize: '0.875rem' }}>
            <strong>{resolvedField.name}</strong>{' '}
            <code className="builder-muted">{resolvedField.slug}</code>
          </div>
          {schemaWritable && (
            <button
              type="button"
              className="btn btn-sm btn-secondary"
              style={{ marginTop: 8 }}
              onClick={() => onOpenEditField(resolvedField)}
            >
              Edit field definition
            </button>
          )}
        </div>
      )}
      {unresolved && (
        <div className="builder-orphan" role="status">
          <strong>Field not in dictionary</strong>
          <p className="builder-muted" style={{ marginTop: 6, fontSize: '0.8125rem' }}>
            This placement has no matching schema field (orphan template slot, renamed slug, or deleted field).
            Create the field or bind to an existing one.
          </p>
          {schemaWritable && (
            <button
              type="button"
              className="btn btn-sm btn-primary"
              style={{ marginTop: 10, width: '100%' }}
              onClick={() =>
                onOpenCreateField({
                  suggestedName: suggestedName || undefined,
                  suggestedSlug: suggestedSlug || undefined,
                  bindSelection,
                })
              }
            >
              + Create field for this placement
            </button>
          )}
          <label className="field-label" style={{ marginTop: 12 }}>
            Bind to existing field
            <select
              className="input"
              value=""
              onChange={(e) => {
                const id = e.target.value;
                if (!id) return;
                const f = fields.find((x) => x.id === id);
                if (!f) return;
                onChange(M.bindItemToField(layout, regionIndex, rowIndex, columnIndex, itemIndex, f.id, f.slug));
              }}
            >
              <option value="">{fields.length ? 'Choose field…' : 'No fields yet — create one first'}</option>
              {bindOptions.map((f) => (
                <option key={f.id} value={f.id}>
                  {f.name} ({f.slug})
                </option>
              ))}
            </select>
          </label>
        </div>
      )}
      <label className="field-label">
        Label (optional)
        <input
          className="input"
          value={p.label ?? ''}
          placeholder="Default: schema form label or field name"
          onChange={(e) => patchPresentation({ label: e.target.value || null })}
        />
      </label>
      <label className="field-label">
        Placeholder
        <input className="input" value={p.placeholder} onChange={(e) => patchPresentation({ placeholder: e.target.value })} />
      </label>
      <label className="field-label">
        Help text
        <textarea
          className="input"
          rows={2}
          value={p.helpText}
          onChange={(e) => patchPresentation({ helpText: e.target.value })}
        />
      </label>
      <label className="field-label row">
        <input type="checkbox" checked={p.readOnly} onChange={(e) => patchPresentation({ readOnly: e.target.checked })} />
        Read-only
      </label>
      <label className="field-label row">
        <input type="checkbox" checked={p.hidden} onChange={(e) => patchPresentation({ hidden: e.target.checked })} />
        Hidden
      </label>
      <label className="field-label">
        Width
        <select className="input" value={p.width} onChange={(e) => patchPresentation({ width: e.target.value })}>
          <option value="full">full</option>
          <option value="half">half</option>
          <option value="third">third</option>
        </select>
      </label>
      <label className="field-label">
        Component hint
        <input
          className="input"
          value={p.componentHint}
          onChange={(e) => patchPresentation({ componentHint: e.target.value })}
        />
      </label>
      <button
        type="button"
        className="btn btn-danger btn-sm"
        style={{ marginTop: 16, width: '100%' }}
        onClick={() => {
          if (!window.confirm('Remove this field from the form?')) return;
          onChange(M.removeItem(layout, regionIndex, rowIndex, columnIndex, itemIndex));
          onClearSelection();
        }}
      >
        Remove from form
      </button>
    </aside>
  );
}

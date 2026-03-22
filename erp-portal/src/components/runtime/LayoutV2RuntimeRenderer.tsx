import { useMemo, useState, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { isDocumentNumberFieldType, type EntityFieldDto } from '../../api/schemas';
import type { LayoutItem, LayoutItemAction, LayoutRegion, LayoutRow } from '../../types/formLayout';
import { defaultPresentation, isActionItem, isSafeActionHref, resolveLayoutItemField } from '../../utils/layoutV2';

type Props = {
  regions: LayoutRegion[];
  fields: EntityFieldDto[];
  values: Record<string, unknown>;
  onChange: (fieldSlug: string, value: unknown) => void;
  /** No record write or whole form read-only */
  disabled: boolean;
  canPiiRead: boolean;
  /** When true, group consecutive `tab` regions with the same `tabGroupId` into one tab strip. */
  useTabGroups: boolean;
  /** Wired on record entry for layout items with `kind: "action"` (save / cancel). */
  onLayoutAction?: (action: 'save' | 'cancel') => void;
};

function collectTabGroups(regions: LayoutRegion[]): { kind: 'single'; region: LayoutRegion } | { kind: 'tabs'; groupId: string | null; regions: LayoutRegion[] }[] {
  const out: { kind: 'single'; region: LayoutRegion } | { kind: 'tabs'; groupId: string | null; regions: LayoutRegion[] }[] = [];
  let i = 0;
  while (i < regions.length) {
    const r = regions[i];
    if (r.role === 'tab') {
      const gid = r.tabGroupId;
      const group: LayoutRegion[] = [r];
      let j = i + 1;
      while (j < regions.length) {
        const n = regions[j];
        if (n.role === 'tab' && n.tabGroupId === gid) {
          group.push(n);
          j++;
        } else {
          break;
        }
      }
      out.push({ kind: 'tabs', groupId: gid, regions: group });
      i = j;
    } else {
      out.push({ kind: 'single', region: r });
      i++;
    }
  }
  return out;
}

function actionButtonClass(variant: LayoutItemAction['variant'], action: LayoutItemAction['action']): string {
  const v =
    variant ??
    (action === 'save' ? 'primary' : action === 'cancel' ? 'secondary' : 'link');
  if (v === 'primary') return 'btn btn-primary';
  if (v === 'secondary') return 'btn btn-secondary';
  return 'link-btn';
}

function LayoutActionControl({
  item,
  disabled,
  onLayoutAction,
}: {
  item: LayoutItemAction;
  disabled: boolean;
  onLayoutAction?: (action: 'save' | 'cancel') => void;
}) {
  const cls = actionButtonClass(item.variant, item.action);

  if (item.action === 'link') {
    const href = item.href?.trim() ?? '';
    if (!isSafeActionHref(href)) {
      return (
        <div className="runtime-field runtime-layout-action">
          <p className="text-error" style={{ fontSize: '0.875rem' }}>
            Invalid or missing link URL
          </p>
        </div>
      );
    }
    const isAppPath = href.startsWith('/') && !href.startsWith('//');
    const target = item.openInNewTab ? '_blank' : undefined;
    const rel = item.openInNewTab ? 'noopener noreferrer' : undefined;
    if (isAppPath) {
      return (
        <div className="runtime-field runtime-layout-action">
          <Link to={href} className={cls} target={target} rel={rel}>
            {item.label}
          </Link>
        </div>
      );
    }
    return (
      <div className="runtime-field runtime-layout-action">
        <a href={href} className={cls} target={target} rel={rel}>
          {item.label}
        </a>
      </div>
    );
  }

  if (item.action === 'save') {
    return (
      <div className="runtime-field runtime-layout-action">
        <button
          type="button"
          className={cls}
          disabled={disabled || !onLayoutAction}
          onClick={() => onLayoutAction?.('save')}
        >
          {item.label}
        </button>
      </div>
    );
  }

  return (
    <div className="runtime-field runtime-layout-action">
      <button
        type="button"
        className={cls}
        disabled={!onLayoutAction}
        onClick={() => onLayoutAction?.('cancel')}
      >
        {item.label}
      </button>
    </div>
  );
}

function FieldControl({
  item,
  field,
  value,
  disabled,
  canPiiRead,
  onChange,
}: {
  item: LayoutItem;
  field: EntityFieldDto;
  value: unknown;
  disabled: boolean;
  canPiiRead: boolean;
  onChange: (slug: string, v: unknown) => void;
}) {
  const pres = item.presentation ?? defaultPresentation();
  if (pres.hidden) return null;

  const slug = field.slug;
  const schemaLabel = field.labelOverride?.trim() ? field.labelOverride : field.name;
  const label = pres.label?.trim() ? pres.label : schemaLabel;
  const piiLocked = field.pii && !canPiiRead;
  const ro = disabled || pres.readOnly || piiLocked;
  const ft = (field.fieldType || 'text').toLowerCase();

  let control: ReactNode;
  if (piiLocked) {
    control = (
      <input className="input" type="text" value="—" readOnly disabled title="PII hidden (missing entity_builder:pii:read)" />
    );
  } else if (ft === 'boolean') {
    const checked = value === true || value === 'true';
    control = (
      <label className="form-check">
        <input
          type="checkbox"
          checked={checked}
          disabled={ro}
          onChange={(e) => onChange(slug, e.target.checked)}
        />
        <span>{label}</span>
      </label>
    );
    return (
      <div className="runtime-field" key={item.id}>
        {control}
        {pres.helpText ? <p className="builder-muted">{pres.helpText}</p> : null}
      </div>
    );
  } else if (ft === 'document_number') {
    const str = value === null || value === undefined ? '' : String(value);
    const formLocked = disabled || piiLocked || pres.readOnly;
    control = (
      <input
        className="input"
        type="text"
        readOnly
        disabled={formLocked}
        value={str}
        placeholder={str ? '' : 'Assigned when the record is saved'}
        title="Stored on the record as businessDocumentNumber; not edited as EAV."
      />
    );
  } else if (ft === 'number') {
    const str = value === null || value === undefined ? '' : String(value);
    control = (
      <input
        className="form-input"
        type="text"
        inputMode="decimal"
        placeholder={pres.placeholder || ''}
        value={str}
        readOnly={ro}
        disabled={ro}
        onChange={(e) => onChange(slug, e.target.value === '' ? null : e.target.value)}
      />
    );
  } else if (ft === 'date') {
    const str = value === null || value === undefined ? '' : String(value);
    control = (
      <input
        className="input"
        type="text"
        placeholder="ISO-8601 instant, e.g. 2024-01-15T12:00:00Z"
        value={str}
        readOnly={ro}
        disabled={ro}
        onChange={(e) => onChange(slug, e.target.value === '' ? null : e.target.value)}
      />
    );
  } else if (ft === 'reference') {
    const str = value === null || value === undefined ? '' : String(value);
    control = (
      <input
        className="input"
        type="text"
        readOnly={ro}
        disabled={ro}
        title="Reference picker not implemented; paste record UUID"
        value={str}
        onChange={(e) => onChange(slug, e.target.value === '' ? null : e.target.value)}
      />
    );
  } else {
    const str = value === null || value === undefined ? '' : String(value);
    control = (
      <input
        className="input"
        type="text"
        placeholder={pres.placeholder || ''}
        value={str}
        readOnly={ro}
        disabled={ro}
        onChange={(e) => onChange(slug, e.target.value === '' ? null : e.target.value)}
      />
    );
  }

  return (
    <div className="runtime-field" key={item.id}>
      {ft !== 'boolean' ? (
        <label className="field-label">
          {label}
          {field.required ? <span className="text-error"> *</span> : null}
        </label>
      ) : null}
      {control}
      {pres.helpText ? <p className="builder-muted">{pres.helpText}</p> : null}
    </div>
  );
}

function renderRow(
  row: LayoutRow,
  fields: EntityFieldDto[],
  values: Record<string, unknown>,
  onChange: (slug: string, v: unknown) => void,
  disabled: boolean,
  canPiiRead: boolean,
  onLayoutAction?: (action: 'save' | 'cancel') => void
) {
  return (
    <div className="runtime-row" key={row.id}>
      {row.columns.map((col) => (
        <div className="runtime-col" key={col.id} style={{ flex: col.span, minWidth: 120 }}>
          {col.items.map((item) => {
            if (isActionItem(item)) {
              return (
                <LayoutActionControl
                  key={item.id}
                  item={item}
                  disabled={disabled}
                  onLayoutAction={onLayoutAction}
                />
              );
            }
            const field = resolveLayoutItemField(item, fields);
            if (!item.fieldId || !field) {
              return item.fieldSlug ? (
                <p key={item.id} className="builder-muted">
                  Unknown field ({item.fieldSlug})
                </p>
              ) : null;
            }
            const v = values[field.slug];
            return (
              <FieldControl
                key={item.id}
                item={item}
                field={field}
                value={v}
                disabled={disabled}
                canPiiRead={canPiiRead}
                onChange={onChange}
              />
            );
          })}
        </div>
      ))}
    </div>
  );
}

function RegionBlock({
  region,
  fields,
  values,
  onChange,
  disabled,
  canPiiRead,
  onLayoutAction,
}: {
  region: LayoutRegion;
  fields: EntityFieldDto[];
  values: Record<string, unknown>;
  onChange: (slug: string, v: unknown) => void;
  disabled: boolean;
  canPiiRead: boolean;
  onLayoutAction?: (action: 'save' | 'cancel') => void;
}) {
  const relPlaceholder = region.binding?.kind === 'entity_relationship';
  return (
    <section className="runtime-region" key={region.id}>
      {region.title ? <h3 className="runtime-region-title">{region.title}</h3> : null}
      {relPlaceholder ? (
        <p className="builder-muted">Line items / related records are not available in this runtime yet.</p>
      ) : (
        region.rows.map((row) =>
          renderRow(row, fields, values, onChange, disabled, canPiiRead, onLayoutAction)
        )
      )}
    </section>
  );
}

function TabGroupBlock({
  regions,
  fields,
  values,
  onChange,
  disabled,
  canPiiRead,
  onLayoutAction,
}: {
  regions: LayoutRegion[];
  fields: EntityFieldDto[];
  values: Record<string, unknown>;
  onChange: (slug: string, v: unknown) => void;
  disabled: boolean;
  canPiiRead: boolean;
  onLayoutAction?: (action: 'save' | 'cancel') => void;
}) {
  const [idx, setIdx] = useState(0);
  const safe = Math.min(idx, Math.max(0, regions.length - 1));
  const active = regions[safe] ?? regions[0];

  return (
    <div className="runtime-tab-group">
      <div className="runtime-tab-bar" role="tablist">
        {regions.map((r, i) => (
          <button
            key={r.id}
            type="button"
            role="tab"
            aria-selected={i === safe}
            className={`runtime-tab ${i === safe ? 'runtime-tab-active' : ''}`}
            onClick={() => setIdx(i)}
          >
            {r.title || `Tab ${i + 1}`}
          </button>
        ))}
      </div>
      {active ? (
        <RegionBlock
          region={active}
          fields={fields}
          values={values}
          onChange={onChange}
          disabled={disabled}
          canPiiRead={canPiiRead}
          onLayoutAction={onLayoutAction}
        />
      ) : null}
    </div>
  );
}

export function LayoutV2RuntimeRenderer({
  regions,
  fields,
  values,
  onChange,
  disabled,
  canPiiRead,
  useTabGroups,
  onLayoutAction,
}: Props) {
  const blocks = useMemo(() => {
    if (!useTabGroups) {
      return regions.map((r) => ({ kind: 'single' as const, region: r }));
    }
    return collectTabGroups(regions);
  }, [regions, useTabGroups]);

  return (
    <div className="runtime-layout">
      {blocks.map((b, i) =>
        b.kind === 'single' ? (
          <RegionBlock
            key={b.region.id}
            region={b.region}
            fields={fields}
            values={values}
            onChange={onChange}
            disabled={disabled}
            canPiiRead={canPiiRead}
            onLayoutAction={onLayoutAction}
          />
        ) : (
          <TabGroupBlock
            key={`tabs-${b.groupId ?? i}`}
            regions={b.regions}
            fields={fields}
            values={values}
            onChange={onChange}
            disabled={disabled}
            canPiiRead={canPiiRead}
            onLayoutAction={onLayoutAction}
          />
        )
      )}
    </div>
  );
}

/** Collect slugs for required fields that appear in these regions and are empty in `values`. */
export function validateRequiredInRegions(
  regions: LayoutRegion[],
  fields: EntityFieldDto[],
  values: Record<string, unknown>
): string[] {
  const missing: string[] = [];
  const seen = new Set<string>();
  for (const region of regions) {
    for (const row of region.rows) {
      for (const col of row.columns) {
        for (const item of col.items) {
          if (isActionItem(item)) continue;
          const field = resolveLayoutItemField(item, fields);
          if (!field || !field.required) continue;
          if (isDocumentNumberFieldType(field.fieldType)) continue;
          if (item.presentation?.hidden) continue;
          const slug = field.slug;
          if (seen.has(slug)) continue;
          seen.add(slug);
          const v = values[slug];
          if (v === undefined || v === null || v === '') {
            missing.push(slug);
          }
        }
      }
    }
  }
  return missing;
}

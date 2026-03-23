import { useId, useMemo, useState, type CSSProperties, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { isDocumentNumberFieldType, type EntityFieldDto } from '../../api/schemas';
import type { LayoutItem, LayoutItemAction, LayoutRegion, LayoutRow } from '../../types/formLayout';
import {
  fieldTypeSupportsTextLengthConstraints,
  readLengthConstraintFromConfig,
} from '../../utils/fieldTextConstraints';
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
  /** Inline validation messages keyed by field slug (shown under the control). */
  fieldErrors?: Record<string, string>;
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

/** Matches form builder Presentation → Width (full / half / third). */
function presentationInputBoxStyle(presWidth: string | undefined): Pick<CSSProperties, 'maxWidth' | 'width'> {
  const w = (presWidth ?? 'full').trim();
  if (w === 'half') return { maxWidth: '20rem', width: '100%' };
  if (w === 'third') return { maxWidth: '14rem', width: '100%' };
  return { width: '100%' };
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
  fieldError,
}: {
  item: LayoutItem;
  field: EntityFieldDto;
  value: unknown;
  disabled: boolean;
  canPiiRead: boolean;
  onChange: (slug: string, v: unknown) => void;
  fieldError?: string;
}) {
  const inputId = useId();
  const errId = useId();
  const pres = item.presentation ?? defaultPresentation();
  if (pres.hidden) return null;

  const slug = field.slug;
  const schemaLabel = field.labelOverride?.trim() ? field.labelOverride : field.name;
  const label = pres.label?.trim() ? pres.label : schemaLabel;
  const piiLocked = field.pii && !canPiiRead;
  const ro = disabled || pres.readOnly || piiLocked;
  const ft = (field.fieldType || 'text').toLowerCase();
  const boxStyle = presentationInputBoxStyle(pres.width);
  const cfg = (field.config ?? undefined) as Record<string, unknown> | undefined;
  const maxLen = fieldTypeSupportsTextLengthConstraints(ft) ? readLengthConstraintFromConfig(cfg, 'maxLength') : undefined;
  const minLen = fieldTypeSupportsTextLengthConstraints(ft) ? readLengthConstraintFromConfig(cfg, 'minLength') : undefined;
  const invalid = Boolean(fieldError);
  const ariaErr = invalid ? { 'aria-invalid': true as const, 'aria-describedby': errId } : {};

  const errorNode =
    fieldError ? (
      <p id={errId} className="runtime-field-error" role="alert">
        {fieldError}
      </p>
    ) : null;

  let control: ReactNode;
  if (piiLocked) {
    control = (
      <input
        className="input"
        type="text"
        value="—"
        readOnly
        disabled
        title="PII hidden (missing entity_builder:pii:read)"
        style={boxStyle}
      />
    );
  } else if (ft === 'boolean') {
    const checked = value === true || value === 'true';
    control = (
      <label className="form-check">
        <input
          id={inputId}
          type="checkbox"
          checked={checked}
          disabled={ro}
          {...ariaErr}
          onChange={(e) => onChange(slug, e.target.checked)}
        />
        <span>{label}</span>
      </label>
    );
    return (
      <div className="runtime-field" key={item.id}>
        {control}
        {errorNode}
        {pres.helpText ? <p className="builder-muted">{pres.helpText}</p> : null}
      </div>
    );
  } else if (ft === 'document_number') {
    const str = value === null || value === undefined ? '' : String(value);
    const formLocked = disabled || piiLocked || pres.readOnly;
    control = (
      <input
        id={inputId}
        className={`input${invalid ? ' input-state-error' : ''}`}
        type="text"
        readOnly
        disabled={formLocked}
        value={str}
        placeholder={str ? '' : 'Assigned when the record is saved'}
        title="Stored on the record as businessDocumentNumber; not edited as EAV."
        style={boxStyle}
        {...ariaErr}
      />
    );
  } else if (ft === 'number') {
    const str = value === null || value === undefined ? '' : String(value);
    control = (
      <input
        id={inputId}
        className={`form-input${invalid ? ' input-state-error' : ''}`}
        type="text"
        inputMode="decimal"
        placeholder={pres.placeholder || ''}
        value={str}
        readOnly={ro}
        disabled={ro}
        style={boxStyle}
        {...ariaErr}
        onChange={(e) => onChange(slug, e.target.value === '' ? null : e.target.value)}
      />
    );
  } else if (ft === 'date') {
    const str = value === null || value === undefined ? '' : String(value);
    control = (
      <input
        id={inputId}
        className={`input${invalid ? ' input-state-error' : ''}`}
        type="text"
        placeholder="ISO-8601 instant, e.g. 2024-01-15T12:00:00Z"
        value={str}
        readOnly={ro}
        disabled={ro}
        style={boxStyle}
        minLength={minLen}
        maxLength={maxLen}
        {...ariaErr}
        onChange={(e) => onChange(slug, e.target.value === '' ? null : e.target.value)}
      />
    );
  } else if (ft === 'reference') {
    const str = value === null || value === undefined ? '' : String(value);
    control = (
      <input
        id={inputId}
        className={`input${invalid ? ' input-state-error' : ''}`}
        type="text"
        readOnly={ro}
        disabled={ro}
        title="Reference picker not implemented; paste record UUID"
        value={str}
        style={boxStyle}
        minLength={minLen}
        maxLength={maxLen}
        {...ariaErr}
        onChange={(e) => onChange(slug, e.target.value === '' ? null : e.target.value)}
      />
    );
  } else {
    const str = value === null || value === undefined ? '' : String(value);
    control = (
      <input
        id={inputId}
        className={`input${invalid ? ' input-state-error' : ''}`}
        type="text"
        placeholder={pres.placeholder || ''}
        value={str}
        readOnly={ro}
        disabled={ro}
        style={boxStyle}
        minLength={minLen}
        maxLength={maxLen}
        {...ariaErr}
        onChange={(e) => onChange(slug, e.target.value === '' ? null : e.target.value)}
      />
    );
  }

  return (
    <div className="runtime-field" key={item.id}>
      {ft !== 'boolean' ? (
        <label className="field-label" htmlFor={inputId}>
          {label}
          {field.required ? <span className="text-error"> *</span> : null}
        </label>
      ) : null}
      {control}
      {errorNode}
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
  onLayoutAction: ((action: 'save' | 'cancel') => void) | undefined,
  fieldErrors: Record<string, string> | undefined
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
                fieldError={fieldErrors?.[field.slug]}
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
  fieldErrors,
}: {
  region: LayoutRegion;
  fields: EntityFieldDto[];
  values: Record<string, unknown>;
  onChange: (slug: string, v: unknown) => void;
  disabled: boolean;
  canPiiRead: boolean;
  onLayoutAction?: (action: 'save' | 'cancel') => void;
  fieldErrors?: Record<string, string>;
}) {
  const relPlaceholder = region.binding?.kind === 'entity_relationship';
  return (
    <section className="runtime-region" key={region.id}>
      {region.title ? <h3 className="runtime-region-title">{region.title}</h3> : null}
      {relPlaceholder ? (
        <p className="builder-muted">Line items / related records are not available in this runtime yet.</p>
      ) : (
        region.rows.map((row) =>
          renderRow(row, fields, values, onChange, disabled, canPiiRead, onLayoutAction, fieldErrors)
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
  fieldErrors,
}: {
  regions: LayoutRegion[];
  fields: EntityFieldDto[];
  values: Record<string, unknown>;
  onChange: (slug: string, v: unknown) => void;
  disabled: boolean;
  canPiiRead: boolean;
  onLayoutAction?: (action: 'save' | 'cancel') => void;
  fieldErrors?: Record<string, string>;
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
          fieldErrors={fieldErrors}
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
  fieldErrors,
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
            fieldErrors={fieldErrors}
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
            fieldErrors={fieldErrors}
          />
        )
      )}
    </div>
  );
}

function fieldDisplayLabel(field: EntityFieldDto): string {
  return field.labelOverride?.trim() || field.name;
}

/**
 * Required-field and text-length validation for visible layout regions.
 * Returns a map of field slug → message for inline display under inputs.
 */
export function buildInlineFieldErrorsForRegions(
  regions: LayoutRegion[],
  fields: EntityFieldDto[],
  values: Record<string, unknown>
): Record<string, string> {
  const errors: Record<string, string> = {};

  const seenRequired = new Set<string>();
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
          if (!fieldTypeSupportsTextLengthConstraints(field.fieldType)) continue;
          const slug = field.slug;
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

import { useId, useMemo, useState, type CSSProperties, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { Anchor, Button, Checkbox, Text, TextInput, Title } from '@mantine/core';
import type { EntityDto, EntityFieldDto, EntityRelationshipDto } from '../../api/schemas';
import type { LayoutItem, LayoutItemAction, LayoutRegion, LayoutRow } from '../../types/formLayout';
import {
  fieldTypeSupportsTextLengthConstraints,
  readLengthConstraintFromConfig,
} from '../../utils/fieldTextConstraints';
import { defaultPresentation, isActionItem, isSafeActionHref, resolveLayoutItemField } from '../../utils/layoutV2';
import { resolveReferenceFieldCollectionRelationship } from '../../utils/referenceFieldConfig';
import { ReferenceRecordLookupField } from './ReferenceRecordLookupField';
import { RelatedRecordsRegion } from './RelatedRecordsRegion';
import type { FieldUiOverrides } from '../../utils/businessRuleUi';

export type LayoutRelatedRuntimeContext = {
  tenantId: string;
  hostEntityId: string;
  parentRecordId: string | null;
  relationships: EntityRelationshipDto[];
  allEntities: EntityDto[];
  canWrite: boolean;
};

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
  /** Dynamic visibility / read-only / required from business rules (UI surface). */
  fieldUiOverrides?: FieldUiOverrides;
  /** Related-record regions ({@code entity_relationship} bindings). Omit in environments that do not load relationships. */
  relatedContext?: LayoutRelatedRuntimeContext;
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

function layoutActionButtonVariant(
  variant: LayoutItemAction['variant'],
  action: LayoutItemAction['action']
): 'filled' | 'default' | 'subtle' {
  const v =
    variant ?? (action === 'save' ? 'primary' : action === 'cancel' ? 'secondary' : 'link');
  if (v === 'primary') return 'filled';
  if (v === 'secondary') return 'default';
  return 'subtle';
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
  const btnVariant = layoutActionButtonVariant(item.variant, item.action);

  if (item.action === 'link') {
    const href = item.href?.trim() ?? '';
    if (!isSafeActionHref(href)) {
      return (
        <div className="runtime-field runtime-layout-action">
          <Text c="red" size="sm">
            Invalid or missing link URL
          </Text>
        </div>
      );
    }
    const isAppPath = href.startsWith('/') && !href.startsWith('//');
    const target = item.openInNewTab ? '_blank' : undefined;
    const rel = item.openInNewTab ? 'noopener noreferrer' : undefined;
    if (isAppPath) {
      return (
        <div className="runtime-field runtime-layout-action">
          <Anchor component={Link} to={href} target={target} rel={rel} size="sm" underline="hover">
            {item.label}
          </Anchor>
        </div>
      );
    }
    return (
      <div className="runtime-field runtime-layout-action">
        <Anchor href={href} target={target} rel={rel} size="sm" underline="hover">
          {item.label}
        </Anchor>
      </div>
    );
  }

  if (item.action === 'save') {
    return (
      <div className="runtime-field runtime-layout-action">
        <Button
          type="button"
          variant={btnVariant}
          disabled={disabled || !onLayoutAction}
          onClick={() => onLayoutAction?.('save')}
        >
          {item.label}
        </Button>
      </div>
    );
  }

  return (
    <div className="runtime-field runtime-layout-action">
      <Button
        type="button"
        variant={btnVariant}
        disabled={!onLayoutAction}
        onClick={() => onLayoutAction?.('cancel')}
      >
        {item.label}
      </Button>
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
  fieldUiOverrides,
  relatedContext,
}: {
  item: LayoutItem;
  field: EntityFieldDto;
  value: unknown;
  disabled: boolean;
  canPiiRead: boolean;
  onChange: (slug: string, v: unknown) => void;
  fieldError?: string;
  fieldUiOverrides?: FieldUiOverrides;
  relatedContext?: LayoutRelatedRuntimeContext;
}) {
  const inputId = useId();
  const pres = item.presentation ?? defaultPresentation();
  const ui = fieldUiOverrides?.[field.slug];
  if (pres.hidden || ui?.hidden) return null;

  const slug = field.slug;
  const schemaLabel = field.labelOverride?.trim() ? field.labelOverride : field.name;
  const label = pres.label?.trim() ? pres.label : schemaLabel;
  const piiLocked = field.pii && !canPiiRead;
  const ro = disabled || pres.readOnly || piiLocked || ui?.readOnly === true;
  const requiredEffective = field.required || ui?.required === true;
  const ft = (field.fieldType || 'text').toLowerCase();
  const boxStyle = presentationInputBoxStyle(pres.width);
  const cfg = (field.config ?? undefined) as Record<string, unknown> | undefined;
  const maxLen = fieldTypeSupportsTextLengthConstraints(ft) ? readLengthConstraintFromConfig(cfg, 'maxLength') : undefined;
  const minLen = fieldTypeSupportsTextLengthConstraints(ft) ? readLengthConstraintFromConfig(cfg, 'minLength') : undefined;

  const labelNode = (
    <>
      {label}
      {requiredEffective ? (
        <Text span c="red" component="span">
          {' '}
          *
        </Text>
      ) : null}
    </>
  );

  if (piiLocked) {
    return (
      <div className="runtime-field" key={item.id}>
        <TextInput
          label={labelNode}
          value="—"
          readOnly
          disabled
          title="PII hidden (missing entity_builder:pii:read)"
          style={boxStyle}
          size="sm"
        />
      </div>
    );
  }

  if (ft === 'boolean') {
    const checked = value === true || value === 'true';
    return (
      <div className="runtime-field" key={item.id}>
        <Checkbox
          id={inputId}
          label={label}
          checked={checked}
          disabled={ro}
          error={fieldError}
          description={pres.helpText}
          onChange={(e) => onChange(slug, e.currentTarget.checked)}
        />
      </div>
    );
  }

  let control: ReactNode;
  if (ft === 'document_number') {
    const str = value === null || value === undefined ? '' : String(value);
    const formLocked = disabled || piiLocked || pres.readOnly;
    control = (
      <TextInput
        id={inputId}
        label={labelNode}
        readOnly
        disabled={formLocked}
        value={str}
        placeholder={str ? '' : 'Assigned when the record is saved'}
        title="Stored on the record as businessDocumentNumber; not edited as EAV."
        style={boxStyle}
        error={fieldError}
        description={pres.helpText}
        size="sm"
      />
    );
  } else if (ft === 'number') {
    const str = value === null || value === undefined ? '' : String(value);
    control = (
      <TextInput
        id={inputId}
        label={labelNode}
        type="text"
        inputMode="decimal"
        placeholder={pres.placeholder || ''}
        value={str}
        readOnly={ro}
        disabled={ro}
        style={boxStyle}
        error={fieldError}
        description={pres.helpText}
        minLength={minLen}
        maxLength={maxLen}
        size="sm"
        onChange={(e) => onChange(slug, e.target.value === '' ? null : e.target.value)}
      />
    );
  } else if (ft === 'date') {
    const str = value === null || value === undefined ? '' : String(value);
    control = (
      <TextInput
        id={inputId}
        label={labelNode}
        type="text"
        placeholder="ISO-8601 instant, e.g. 2024-01-15T12:00:00Z"
        value={str}
        readOnly={ro}
        disabled={ro}
        style={boxStyle}
        minLength={minLen}
        maxLength={maxLen}
        error={fieldError}
        description={pres.helpText}
        size="sm"
        onChange={(e) => onChange(slug, e.target.value === '' ? null : e.target.value)}
      />
    );
  } else if (ft === 'reference') {
    const collectionRel =
      relatedContext &&
      resolveReferenceFieldCollectionRelationship(
        field,
        relatedContext.hostEntityId,
        relatedContext.relationships,
        relatedContext.allEntities
      );
    if (collectionRel) {
      control = (
        <>
          <Title order={5} size="sm" mb="xs">
            {labelNode}
          </Title>
          {pres.helpText ? (
            <Text size="sm" c="dimmed" mb="sm">
              {pres.helpText}
            </Text>
          ) : null}
          <RelatedRecordsRegion
            tenantId={relatedContext!.tenantId}
            hostEntityId={relatedContext!.hostEntityId}
            parentRecordId={relatedContext!.parentRecordId}
            relationshipId={collectionRel.id}
            relationships={relatedContext!.relationships}
            allEntities={relatedContext!.allEntities}
            canWrite={relatedContext!.canWrite && !ro}
          />
        </>
      );
    } else {
      control = (
        <ReferenceRecordLookupField
          field={field}
          value={value}
          onChange={onChange}
          disabled={ro}
          labelNode={labelNode}
          boxStyle={boxStyle}
          description={pres.helpText}
          fieldError={fieldError}
          inputId={inputId}
        />
      );
    }
  } else {
    const str = value === null || value === undefined ? '' : String(value);
    control = (
      <TextInput
        id={inputId}
        label={labelNode}
        type="text"
        placeholder={pres.placeholder || ''}
        value={str}
        readOnly={ro}
        disabled={ro}
        style={boxStyle}
        minLength={minLen}
        maxLength={maxLen}
        error={fieldError}
        description={pres.helpText}
        size="sm"
        onChange={(e) => onChange(slug, e.target.value === '' ? null : e.target.value)}
      />
    );
  }

  return (
    <div className="runtime-field" key={item.id}>
      {control}
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
  fieldErrors: Record<string, string> | undefined,
  fieldUiOverrides: FieldUiOverrides | undefined,
  relatedContext?: LayoutRelatedRuntimeContext
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
                <Text key={item.id} size="sm" c="dimmed">
                  Unknown field ({item.fieldSlug})
                </Text>
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
                fieldUiOverrides={fieldUiOverrides}
                relatedContext={relatedContext}
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
  fieldUiOverrides,
  relatedContext,
}: {
  region: LayoutRegion;
  fields: EntityFieldDto[];
  values: Record<string, unknown>;
  onChange: (slug: string, v: unknown) => void;
  disabled: boolean;
  canPiiRead: boolean;
  onLayoutAction?: (action: 'save' | 'cancel') => void;
  fieldErrors?: Record<string, string>;
  fieldUiOverrides?: FieldUiOverrides;
  relatedContext?: LayoutRelatedRuntimeContext;
}) {
  const relBinding =
    region.binding?.kind === 'entity_relationship' && region.binding.relationshipId
      ? region.binding
      : null;
  return (
    <section className="runtime-region" key={region.id}>
      {region.title ? (
        <Title order={3} className="runtime-region-title" size="h5" mb="sm">
          {region.title}
        </Title>
      ) : null}
      {relBinding && relatedContext ? (
        <RelatedRecordsRegion
          tenantId={relatedContext.tenantId}
          hostEntityId={relatedContext.hostEntityId}
          parentRecordId={relatedContext.parentRecordId}
          relationshipId={relBinding.relationshipId}
          relationships={relatedContext.relationships}
          allEntities={relatedContext.allEntities}
          canWrite={relatedContext.canWrite && !disabled}
        />
      ) : relBinding && !relatedContext ? (
        <Text size="sm" c="dimmed">
          Related records need relationship data (reload the page).
        </Text>
      ) : (
        region.rows.map((row) =>
          renderRow(
            row,
            fields,
            values,
            onChange,
            disabled,
            canPiiRead,
            onLayoutAction,
            fieldErrors,
            fieldUiOverrides,
            relatedContext
          )
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
  fieldUiOverrides,
  relatedContext,
}: {
  regions: LayoutRegion[];
  fields: EntityFieldDto[];
  values: Record<string, unknown>;
  onChange: (slug: string, v: unknown) => void;
  disabled: boolean;
  canPiiRead: boolean;
  onLayoutAction?: (action: 'save' | 'cancel') => void;
  fieldErrors?: Record<string, string>;
  fieldUiOverrides?: FieldUiOverrides;
  relatedContext?: LayoutRelatedRuntimeContext;
}) {
  const [idx, setIdx] = useState(0);
  const safe = Math.min(idx, Math.max(0, regions.length - 1));
  const active = regions[safe] ?? regions[0];

  return (
    <div className="runtime-tab-group">
      <div className="runtime-tab-bar" role="tablist">
        {regions.map((r, i) => (
          <Button
            key={r.id}
            type="button"
            role="tab"
            size="xs"
            variant={i === safe ? 'filled' : 'default'}
            aria-selected={i === safe}
            className={`runtime-tab ${i === safe ? 'runtime-tab-active' : ''}`}
            onClick={() => setIdx(i)}
          >
            {r.title || `Tab ${i + 1}`}
          </Button>
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
          fieldUiOverrides={fieldUiOverrides}
          relatedContext={relatedContext}
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
  fieldUiOverrides,
  relatedContext,
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
            fieldUiOverrides={fieldUiOverrides}
            relatedContext={relatedContext}
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
            fieldUiOverrides={fieldUiOverrides}
            relatedContext={relatedContext}
          />
        )
      )}
    </div>
  );
}

import { Button, Checkbox, Select, Stack, Text, Textarea, TextInput } from '@mantine/core';
import type { EntityDto, EntityFieldDto, EntityRelationshipDto } from '../../api/schemas';
import type { LayoutActionType, LayoutItemAction, LayoutRegion, LayoutV2, StructureSelection } from '../../types/formLayout';
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

function isValidEntityRelationshipBinding(
  b: LayoutRegion['binding'] | undefined
): b is { kind: 'entity_relationship'; relationshipId: string } {
  return b?.kind === 'entity_relationship' && typeof b.relationshipId === 'string' && b.relationshipId.length > 0;
}

type Props = {
  layout: LayoutV2;
  layoutEntityId: string;
  selection: StructureSelection | null;
  fields: EntityFieldDto[];
  relationships: EntityRelationshipDto[];
  allEntities: EntityDto[];
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
  layoutEntityId,
  selection,
  fields,
  relationships,
  allEntities,
  schemaWritable,
  onChange,
  onClearSelection,
  onOpenCreateField,
  onOpenEditField,
}: Props) {
  if (selection?.kind === 'region') {
    const { regionIndex } = selection;
    const region = layout.regions[regionIndex];
    if (!region) {
      return (
        <aside className="builder-panel">
          <p className="builder-muted">Selection no longer valid.</p>
          <Button variant="default" size="xs" onClick={onClearSelection}>
            Clear
          </Button>
        </aside>
      );
    }
    const outgoing = relationships.filter((r) => r.fromEntityId === layoutEntityId);
    const entityName = (id: string) => allEntities.find((e) => e.id === id)?.name ?? id;
    const relBinding = isValidEntityRelationshipBinding(region.binding) ? region.binding : null;
    const selectData = outgoing.map((r) => ({
      value: r.id,
      label: `${r.name} (${r.slug}) → ${entityName(r.toEntityId)}`,
    }));

    return (
      <aside className="builder-panel">
        <div className="builder-panel-header">
          <h2>Region</h2>
        </div>
        <Stack gap="md">
          <Select
            label="Related records"
            description={
              relBinding
                ? 'Rows are loaded from record links on the parent for this relationship. You can leave field rows empty or add a title only.'
                : 'Optional: show a grid of linked child records for a relationship whose parent is this entity (from side).'
            }
            placeholder={outgoing.length ? 'None — use field rows only' : 'No outgoing relationships defined'}
            data={selectData}
            value={relBinding?.relationshipId ?? null}
            onChange={(id) => {
              if (!id) {
                onChange(M.setRegionBinding(layout, regionIndex, null));
                return;
              }
              onChange(
                M.setRegionBinding(layout, regionIndex, { kind: 'entity_relationship', relationshipId: id })
              );
            }}
            clearable
            disabled={!schemaWritable || outgoing.length === 0}
          />
        </Stack>
      </aside>
    );
  }

  if (!selection || selection.kind !== 'item') {
    return (
      <aside className="builder-panel">
        <div className="builder-panel-header">
          <h2>Properties</h2>
        </div>
        <p className="builder-muted">Select a region, field, or button on the form to edit properties.</p>
      </aside>
    );
  }

  const { regionIndex, rowIndex, columnIndex, itemIndex } = selection;
  const item = layout.regions[regionIndex]?.rows[rowIndex]?.columns[columnIndex]?.items[itemIndex];
  if (!item) {
    return (
      <aside className="builder-panel">
        <p className="builder-muted">Selection no longer valid.</p>
        <Button variant="default" size="xs" onClick={onClearSelection}>
          Clear
        </Button>
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
        <Text size="sm" c="dimmed" mb="md">
          Save and Cancel use the record form&apos;s save and back-to-list actions. Links must use <code>https://</code>{' '}
          or an in-app path starting with <code>/</code> (not <code>//</code>).
        </Text>
        <Stack gap="md">
          <Select
            label="Action"
            disabled={!schemaWritable}
            data={[
              { value: 'save', label: 'Save record' },
              { value: 'cancel', label: 'Cancel (back to list)' },
              { value: 'link', label: 'Open link' },
            ]}
            value={item.action}
            onChange={(v) => v && patchAction({ action: v as LayoutActionType })}
          />
          <TextInput
            label="Label"
            disabled={!schemaWritable}
            value={item.label}
            onChange={(e) => patchAction({ label: e.target.value })}
          />
          {item.action === 'link' ? (
            <>
              <TextInput
                label="URL or path"
                disabled={!schemaWritable}
                value={item.href ?? ''}
                placeholder="https://… or /entities/…"
                onChange={(e) => patchAction({ href: e.target.value })}
              />
              {hrefBad ? (
                <Text c="red" size="sm">
                  Invalid URL. Use https:// or a path starting with / .
                </Text>
              ) : null}
              <Checkbox
                label="Open in new tab"
                disabled={!schemaWritable}
                checked={Boolean(item.openInNewTab)}
                onChange={(e) => patchAction({ openInNewTab: e.currentTarget.checked })}
              />
            </>
          ) : null}
          <Select
            label="Style"
            disabled={!schemaWritable}
            data={[
              { value: '', label: 'Default (by action)' },
              { value: 'primary', label: 'Primary' },
              { value: 'secondary', label: 'Secondary' },
              { value: 'link', label: 'Link style' },
            ]}
            value={item.variant ?? ''}
            onChange={(v) => {
              patchAction({ variant: v === '' || v == null ? undefined : (v as LayoutItemAction['variant']) });
            }}
          />
          {schemaWritable ? (
            <Button
              type="button"
              color="red"
              variant="light"
              fullWidth
              mt="md"
              onClick={() => {
                if (!window.confirm('Remove this button from the form?')) return;
                onChange(M.removeItem(layout, regionIndex, rowIndex, columnIndex, itemIndex));
                onClearSelection();
              }}
            >
              Remove button
            </Button>
          ) : null}
        </Stack>
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
      <Stack gap="md">
        {resolvedField && (
          <div style={{ marginBottom: 4, paddingBottom: 12, borderBottom: '1px solid #e4e4e7' }}>
            <Text size="xs" c="dimmed" mb={6}>
              Schema field
            </Text>
            <Text size="sm">
              <strong>{resolvedField.name}</strong> <code className="builder-muted">{resolvedField.slug}</code>
            </Text>
            {schemaWritable && (
              <Button variant="default" size="xs" mt="sm" onClick={() => onOpenEditField(resolvedField)}>
                Edit field definition
              </Button>
            )}
          </div>
        )}
        {unresolved && (
          <div className="builder-orphan" role="status">
            <Text fw={600} size="sm">
              Field not in dictionary
            </Text>
            <Text size="sm" c="dimmed" mt={6}>
              This placement has no matching schema field (orphan template slot, renamed slug, or deleted field).
              Create the field or bind to an existing one.
            </Text>
            {schemaWritable && (
              <Button
                fullWidth
                mt="sm"
                onClick={() =>
                  onOpenCreateField({
                    suggestedName: suggestedName || undefined,
                    suggestedSlug: suggestedSlug || undefined,
                    bindSelection,
                  })
                }
              >
                + Create field for this placement
              </Button>
            )}
            <Select
              label="Bind to existing field"
              mt="md"
              placeholder={fields.length ? 'Choose field…' : 'No fields yet — create one first'}
              data={bindOptions.map((f) => ({ value: f.id, label: `${f.name} (${f.slug})` }))}
              value={null}
              onChange={(id) => {
                if (!id) return;
                const f = fields.find((x) => x.id === id);
                if (!f) return;
                onChange(M.bindItemToField(layout, regionIndex, rowIndex, columnIndex, itemIndex, f.id, f.slug));
              }}
              clearable
            />
          </div>
        )}
        <TextInput
          label="Label (optional)"
          value={p.label ?? ''}
          placeholder="Default: schema form label or field name"
          onChange={(e) => patchPresentation({ label: e.target.value || null })}
        />
        <TextInput
          label="Placeholder"
          value={p.placeholder}
          onChange={(e) => patchPresentation({ placeholder: e.target.value })}
        />
        <Textarea
          label="Help text"
          rows={2}
          value={p.helpText}
          onChange={(e) => patchPresentation({ helpText: e.target.value })}
        />
        <Checkbox
          label="Read-only"
          checked={p.readOnly}
          onChange={(e) => patchPresentation({ readOnly: e.currentTarget.checked })}
        />
        <Checkbox
          label="Hidden"
          checked={p.hidden}
          onChange={(e) => patchPresentation({ hidden: e.currentTarget.checked })}
        />
        <Select
          label="Width"
          data={[
            { value: 'full', label: 'full' },
            { value: 'half', label: 'half' },
            { value: 'third', label: 'third' },
          ]}
          value={p.width}
          onChange={(v) => v && patchPresentation({ width: v })}
        />
        <TextInput
          label="Component hint"
          value={p.componentHint}
          onChange={(e) => patchPresentation({ componentHint: e.target.value })}
        />
        <Button
          type="button"
          color="red"
          variant="light"
          fullWidth
          mt="md"
          onClick={() => {
            if (!window.confirm('Remove this field from the form?')) return;
            onChange(M.removeItem(layout, regionIndex, rowIndex, columnIndex, itemIndex));
            onClearSelection();
          }}
        >
          Remove from form
        </Button>
      </Stack>
    </aside>
  );
}

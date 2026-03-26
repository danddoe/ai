import { Button, Loader, MultiSelect, Stack, Text } from '@mantine/core';
import { useEffect, useState } from 'react';
import {
  getAssignableEntityStatuses,
  getAssignableEntityStatusesForField,
  getEntityStatusAssignments,
  getEntityStatusAssignmentsForField,
  putEntityStatusAssignments,
  putEntityStatusAssignmentsForField,
} from '../api/schemas';
import { ENTITY_STATUS_ENTITY_SLUG } from '../utils/entityStatusCatalog';

type Layout = 'page' | 'modal';

export type EntityStatusAssignmentScope =
  | { kind: 'entity' }
  | { kind: 'field'; fieldId: string };

type Props = {
  tenantId: string;
  /** Host entity definition id (used for definition-scoped APIs and for field-scoped “available” context). */
  entityId: string;
  scope: EntityStatusAssignmentScope;
  /** When false, show hint that a reference field to entity_status is needed for form reference lookups */
  statusRefFieldPresent: boolean;
  layout?: Layout;
};

export function EntityStatusAssignmentsPanel({
  tenantId,
  entityId,
  scope,
  statusRefFieldPresent,
  layout = 'page',
}: Props) {
  const [available, setAvailable] = useState<{ value: string; label: string }[]>([]);
  const [selected, setSelected] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [savedAt, setSavedAt] = useState<number | null>(null);

  const scopeKey = scope.kind === 'entity' ? 'entity' : `field:${scope.fieldId}`;

  useEffect(() => {
    if (!tenantId || !entityId || (scope.kind === 'field' && !scope.fieldId)) {
      setAvailable([]);
      setSelected([]);
      setLoading(false);
      setError(null);
      return;
    }
    let cancelled = false;
    setLoading(true);
    setError(null);
    const load =
      scope.kind === 'entity'
        ? Promise.all([getAssignableEntityStatuses(tenantId, entityId), getEntityStatusAssignments(tenantId, entityId)])
        : Promise.all([
            getAssignableEntityStatusesForField(entityId, scope.fieldId),
            getEntityStatusAssignmentsForField(entityId, scope.fieldId),
          ]);
    void load
      .then(([availRows, assigned]) => {
        if (cancelled) return;
        setAvailable(availRows.map((a) => ({ value: a.entityStatusId, label: `${a.code} — ${a.label}` })));
        setSelected(assigned.map((r) => r.entityStatusId));
        setLoading(false);
      })
      .catch((err) => {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : 'Failed to load status assignments');
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [tenantId, entityId, scopeKey]);

  async function save() {
    if (!tenantId || !entityId || (scope.kind === 'field' && !scope.fieldId)) return;
    setSaving(true);
    setError(null);
    setSavedAt(null);
    try {
      const rows =
        scope.kind === 'entity'
          ? await putEntityStatusAssignments(tenantId, entityId, selected)
          : await putEntityStatusAssignmentsForField(entityId, scope.fieldId, selected);
      setSelected(rows.map((r) => r.entityStatusId));
      setSavedAt(Date.now());
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Save failed');
    } finally {
      setSaving(false);
    }
  }

  function onSelectionChange(next: string[]) {
    setSelected((prev) => {
      const nextSet = new Set(next);
      const kept = prev.filter((id) => nextSet.has(id));
      const added = next.filter((id) => !prev.includes(id));
      return [...kept, ...added];
    });
  }

  const isPage = layout === 'page';
  const isFieldScope = scope.kind === 'field';

  return (
    <Stack gap="sm" mt={isPage ? 'xl' : 0} maw={isPage ? 560 : undefined}>
      {isPage ? (
        <h2 id="status-assignments" className="page-title" style={{ fontSize: '1.05rem' }}>
          Status assignments
        </h2>
      ) : (
        <Text size="sm" fw={600}>
          Status assignments
        </Text>
      )}
      {isFieldScope ? (
        <Text size="sm" c="dimmed">
          Allowed statuses for <strong>this</strong> reference field (target <strong>{ENTITY_STATUS_ENTITY_SLUG}</strong>).
          When you save at least one status here, form lookups use this list instead of the entity-level assignments for
          this control only.
        </Text>
      ) : (
        <Text size="sm" c="dimmed">
          Choose which platform status values apply for <strong>reference → {ENTITY_STATUS_ENTITY_SLUG}</strong> lookups
          (when no per-field list exists), <strong>assignedForEntityId</strong> on record APIs, and the records list
          status filter. If none are selected at entity level (and no field-level list applies), all visible statuses are
          used.
        </Text>
      )}
      {!statusRefFieldPresent && !isFieldScope && (
        <Text size="sm" c="dimmed" fs="italic">
          This entity has no reference field targeting <strong>{ENTITY_STATUS_ENTITY_SLUG}</strong> yet. You can still
          save assignments here; they take effect for list filters and record lookups once that field exists or for APIs
          that pass <strong>assignedForEntityId</strong>.
        </Text>
      )}
      {loading ? (
        <Loader size="sm" />
      ) : (
        <>
          <MultiSelect
            label="Allowed statuses"
            aria-label="Allowed entity statuses for this entity"
            data={available}
            value={selected}
            onChange={onSelectionChange}
            searchable
            clearable
            nothingFoundMessage="No statuses"
          />
          {error && (
            <Text size="sm" c="red" role="alert">
              {error}
            </Text>
          )}
          <Button type="button" size="sm" loading={saving} onClick={() => void save()}>
            Save assignments
          </Button>
          {savedAt != null && (
            <Text size="xs" c="dimmed" role="status">
              Saved
            </Text>
          )}
        </>
      )}
    </Stack>
  );
}

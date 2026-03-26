import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Button,
  Group,
  Loader,
  Modal,
  ScrollArea,
  Select,
  Table,
  Text,
  TextInput,
  type CSSProperties,
} from '@mantine/core';
import {
  getEntityStatusAssignmentsForField,
  getRecord,
  listFields,
  listRecords,
  lookupRecords,
  type EntityFieldDto,
  type RecordDto,
} from '../../api/schemas';
import { useRecordFormRuntime } from './RecordFormRuntimeContext';
import {
  isEntityStatusReferenceTarget,
  looksLikeRecordUuid,
  readReferenceFieldConfig,
} from '../../utils/referenceFieldConfig';

type Props = {
  field: EntityFieldDto;
  value: unknown;
  onChange: (slug: string, v: unknown) => void;
  disabled: boolean;
  labelNode: React.ReactNode;
  boxStyle: Pick<CSSProperties, 'maxWidth' | 'width'>;
  description?: string | null;
  fieldError?: string;
  inputId: string;
};

function formatCell(v: unknown): string {
  if (v === null || v === undefined) return '—';
  if (typeof v === 'object') return JSON.stringify(v);
  return String(v);
}

/** Preserve schema order; drop duplicate slugs (first occurrence wins). */
function dedupeSlugsPreserveOrder(columnSlugs: string[]): string[] {
  const seen = new Set<string>();
  const out: string[] = [];
  for (const s of columnSlugs) {
    const t = s.trim();
    if (!t) continue;
    const key = t.toLowerCase();
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(t);
  }
  return out;
}

/**
 * Single-line label: fields in the order saved under {@code referenceLookupDisplaySlugs}, joined by {@code " - "}.
 * If no display columns are configured, falls back to the entity default display field.
 */
function buildRecordDisplayLabel(
  rec: RecordDto,
  defaultDisplayFieldSlug: string | null | undefined,
  columnSlugs: string[]
): string {
  const ordered = dedupeSlugsPreserveOrder(columnSlugs);
  if (ordered.length === 0) {
    return resolveLabelFromRecord(rec, defaultDisplayFieldSlug);
  }
  return ordered.map((s) => formatCell(rec.values[s])).join(' - ');
}

function resolveLabelFromRecord(
  rec: { values: Record<string, unknown> },
  defaultDisplayFieldSlug: string | null | undefined
): string {
  if (defaultDisplayFieldSlug) {
    const raw = rec.values[defaultDisplayFieldSlug];
    if (raw !== null && raw !== undefined && String(raw).trim() !== '') {
      return String(raw);
    }
  }
  const first = Object.values(rec.values).find((x) => x !== null && x !== undefined && String(x).trim() !== '');
  if (first !== undefined) return String(first);
  return '—';
}

export function ReferenceRecordLookupField({
  field,
  value,
  onChange,
  disabled,
  labelNode,
  boxStyle,
  description,
  fieldError,
  inputId,
}: Props) {
  const { tenantId, hostEntityId, entityBySlug } = useRecordFormRuntime();
  const slug = field.slug;
  const cfg = useMemo(() => readReferenceFieldConfig(field.config), [field.config]);
  const statusRefTarget = isEntityStatusReferenceTarget(cfg.targetEntitySlug);
  const assignedForEntityId =
    statusRefTarget && hostEntityId?.trim() ? hostEntityId.trim() : undefined;

  /** Only pass field id to list/lookup when field-scoped rows exist (avoids over-filtering). */
  const [fieldScopeResolved, setFieldScopeResolved] = useState(!statusRefTarget);
  const [fieldHasScopedAssignments, setFieldHasScopedAssignments] = useState(false);

  useEffect(() => {
    if (!statusRefTarget || !hostEntityId?.trim() || !field.id?.trim()) {
      setFieldHasScopedAssignments(false);
      setFieldScopeResolved(true);
      return;
    }
    let cancelled = false;
    setFieldScopeResolved(false);
    void getEntityStatusAssignmentsForField(hostEntityId.trim(), field.id.trim())
      .then((rows) => {
        if (!cancelled) {
          setFieldHasScopedAssignments(rows.length > 0);
          setFieldScopeResolved(true);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setFieldHasScopedAssignments(false);
          setFieldScopeResolved(true);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [statusRefTarget, hostEntityId, field.id]);

  const assignedForEntityFieldId =
    statusRefTarget && fieldHasScopedAssignments && field.id?.trim() ? field.id.trim() : undefined;
  const targetEntity = cfg.targetEntitySlug ? entityBySlug[cfg.targetEntitySlug] : undefined;
  const targetEntityId = targetEntity?.id;
  const defaultDisplaySlug = targetEntity?.defaultDisplayFieldSlug ?? undefined;
  const columnSlugs = cfg.lookupDisplaySlugs;

  const [modalOpen, setModalOpen] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const [debouncedTerm, setDebouncedTerm] = useState('');
  const [lookupLoading, setLookupLoading] = useState(false);
  const [lookupItems, setLookupItems] = useState<Awaited<ReturnType<typeof lookupRecords>>['items']>([]);
  const [lookupError, setLookupError] = useState<string | null>(null);

  const [targetFields, setTargetFields] = useState<EntityFieldDto[]>([]);
  const [resolvedLabel, setResolvedLabel] = useState<string | null>(null);
  const [labelLoading, setLabelLoading] = useState(false);

  const [dropdownRecords, setDropdownRecords] = useState<RecordDto[]>([]);
  const [dropdownTotal, setDropdownTotal] = useState(0);
  const [dropdownLoading, setDropdownLoading] = useState(false);
  const [dropdownError, setDropdownError] = useState<string | null>(null);

  useEffect(() => {
    const t = window.setTimeout(() => setDebouncedTerm(searchTerm.trim()), 300);
    return () => window.clearTimeout(t);
  }, [searchTerm]);

  useEffect(() => {
    if (!targetEntityId) {
      setTargetFields([]);
      return;
    }
    let cancelled = false;
    void listFields(targetEntityId).then((flds) => {
      if (!cancelled) setTargetFields(flds);
    });
    return () => {
      cancelled = true;
    };
  }, [targetEntityId]);

  const slugToLabel = useMemo(() => {
    const m = new Map<string, string>();
    for (const f of targetFields) {
      m.set(f.slug, f.displayLabel?.trim() || f.labelOverride?.trim() || f.name);
    }
    return m;
  }, [targetFields]);

  const strVal = value === null || value === undefined ? '' : String(value);

  useEffect(() => {
    if (!tenantId || !targetEntityId || !looksLikeRecordUuid(strVal)) {
      setResolvedLabel(null);
      setLabelLoading(false);
      return;
    }
    let cancelled = false;
    setLabelLoading(true);
    void getRecord(tenantId, targetEntityId, strVal.trim())
      .then((rec) => {
        if (cancelled) return;
        setResolvedLabel(buildRecordDisplayLabel(rec, defaultDisplaySlug, columnSlugs));
      })
      .catch(() => {
        if (!cancelled) setResolvedLabel(null);
      })
      .finally(() => {
        if (!cancelled) setLabelLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [tenantId, targetEntityId, strVal, defaultDisplaySlug, columnSlugs]);

  useEffect(() => {
    if (cfg.uiMode !== 'dropdown' || !tenantId || !targetEntityId) {
      setDropdownRecords([]);
      setDropdownTotal(0);
      return;
    }
    if (statusRefTarget && !fieldScopeResolved) {
      setDropdownRecords([]);
      setDropdownTotal(0);
      setDropdownLoading(true);
      setDropdownError(null);
      return;
    }
    let cancelled = false;
    setDropdownLoading(true);
    setDropdownError(null);
    void listRecords(tenantId, targetEntityId, {
      page: 1,
      pageSize: 200,
      assignedForEntityId: assignedForEntityId ?? undefined,
      assignedForEntityFieldId: assignedForEntityFieldId ?? undefined,
    })
      .then((page) => {
        if (cancelled) return;
        setDropdownRecords(page.items);
        setDropdownTotal(page.total);
      })
      .catch((e: unknown) => {
        if (!cancelled) setDropdownError(e instanceof Error ? e.message : 'Failed to load records');
      })
      .finally(() => {
        if (!cancelled) setDropdownLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [
    cfg.uiMode,
    tenantId,
    targetEntityId,
    assignedForEntityId,
    assignedForEntityFieldId,
    statusRefTarget,
    fieldScopeResolved,
  ]);

  useEffect(() => {
    if (!modalOpen || !tenantId || !targetEntityId) {
      return;
    }
    if (statusRefTarget && !fieldScopeResolved) {
      setLookupItems([]);
      setLookupLoading(false);
      setLookupError(null);
      return;
    }
    if (debouncedTerm.length < 2) {
      setLookupItems([]);
      setLookupError(null);
      setLookupLoading(false);
      return;
    }
    let cancelled = false;
    setLookupLoading(true);
    setLookupError(null);
    void lookupRecords(tenantId, targetEntityId, {
      term: debouncedTerm,
      displaySlugs: cfg.lookupDisplaySlugs,
      limit: 50,
      assignedForEntityId: assignedForEntityId ?? undefined,
      assignedForEntityFieldId: assignedForEntityFieldId ?? undefined,
    })
      .then((res) => {
        if (!cancelled) setLookupItems(res.items);
      })
      .catch((e: unknown) => {
        if (!cancelled) setLookupError(e instanceof Error ? e.message : 'Lookup failed');
      })
      .finally(() => {
        if (!cancelled) setLookupLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [
    modalOpen,
    tenantId,
    targetEntityId,
    debouncedTerm,
    cfg,
    assignedForEntityId,
    assignedForEntityFieldId,
    statusRefTarget,
    fieldScopeResolved,
  ]);

  const openModal = useCallback(() => {
    setSearchTerm('');
    setDebouncedTerm('');
    setLookupItems([]);
    setLookupError(null);
    setModalOpen(true);
  }, []);

  const pickRow = useCallback(
    (recordId: string) => {
      onChange(slug, recordId);
      setModalOpen(false);
    },
    [onChange, slug]
  );

  const clearVal = useCallback(() => {
    onChange(slug, null);
    setResolvedLabel(null);
  }, [onChange, slug]);

  const orderedColumnSlugs = useMemo(() => dedupeSlugsPreserveOrder(columnSlugs), [columnSlugs]);

  const dropdownSelectData = useMemo(() => {
    const rows = dropdownRecords.map((rec) => ({
      value: rec.id,
      label: buildRecordDisplayLabel(rec, defaultDisplaySlug, columnSlugs),
    }));
    if (strVal && looksLikeRecordUuid(strVal) && !rows.some((r) => r.value === strVal)) {
      rows.unshift({
        value: strVal,
        label: resolvedLabel ?? (labelLoading ? 'Loading…' : `${strVal.slice(0, 8)}…`),
      });
    }
    return rows;
  }, [dropdownRecords, defaultDisplaySlug, columnSlugs, strVal, resolvedLabel, labelLoading]);

  if (!cfg.targetEntitySlug) {
    return (
      <>
        <TextInput
          id={inputId}
          label={labelNode}
          value={strVal}
          readOnly={disabled}
          disabled={disabled}
          style={boxStyle}
          error={fieldError}
          description={description ?? 'Reference field has no targetEntitySlug in schema.'}
          size="sm"
        />
      </>
    );
  }

  if (!targetEntity) {
    return (
      <>
        <TextInput
          id={inputId}
          label={labelNode}
          value={strVal}
          readOnly={disabled}
          disabled={disabled}
          style={boxStyle}
          error={fieldError}
          description={description ?? `Unknown target entity slug: ${cfg.targetEntitySlug}`}
          size="sm"
        />
      </>
    );
  }

  if (!tenantId) {
    return (
      <>
        <TextInput
          id={inputId}
          label={labelNode}
          value={strVal}
          readOnly={disabled}
          disabled={disabled}
          style={boxStyle}
          error={fieldError}
          description={description ?? 'Sign in to use reference lookup.'}
          size="sm"
        />
      </>
    );
  }

  if (cfg.uiMode === 'dropdown') {
    return (
      <>
        <Select
          id={inputId}
          label={labelNode}
          description={description ?? undefined}
          placeholder={dropdownLoading ? 'Loading records…' : 'Select a record…'}
          data={dropdownSelectData}
          value={strVal && looksLikeRecordUuid(strVal) ? strVal : null}
          onChange={(v) => onChange(slug, v ?? null)}
          searchable
          clearable
          disabled={disabled || dropdownLoading}
          error={fieldError}
          size="sm"
          style={boxStyle}
          nothingFoundMessage={dropdownLoading ? 'Loading…' : 'No matching options'}
        />
        {dropdownError ? (
          <Text size="xs" c="red" role="alert" mt={4}>
            {dropdownError}
          </Text>
        ) : null}
        {!dropdownError && dropdownTotal > 200 ? (
          <Text size="xs" c="dimmed" mt={4}>
            Showing the first 200 of {dropdownTotal} records. Use Search (popup) mode in field settings to pick others.
          </Text>
        ) : null}
      </>
    );
  }

  return (
    <>
      <Text size="sm" fw={500} component="label" htmlFor={inputId} style={{ display: 'block', marginBottom: 4 }}>
        {labelNode}
      </Text>
      {description ? (
        <Text size="xs" c="dimmed" mb={6}>
          {description}
        </Text>
      ) : null}

      <Group gap="xs" align="flex-start" wrap="nowrap" style={{ ...boxStyle, alignSelf: 'stretch' }}>
        <TextInput
          id={inputId}
          style={{ flex: 1, minWidth: 0 }}
          readOnly
          disabled={disabled}
          value={
            labelLoading
              ? 'Loading…'
              : resolvedLabel
                ? resolvedLabel
                : looksLikeRecordUuid(strVal)
                  ? strVal
                  : strVal || ''
          }
          placeholder="No record selected"
          error={fieldError}
          size="sm"
          styles={{ input: { cursor: disabled ? 'default' : 'pointer' } }}
          onClick={() => !disabled && openModal()}
        />
        <Button type="button" variant="default" size="sm" disabled={disabled} onClick={openModal}>
          Search
        </Button>
        <Button
          type="button"
          variant="subtle"
          size="sm"
          disabled={disabled || !strVal}
          onClick={clearVal}
          aria-label="Clear reference"
        >
          Clear
        </Button>
      </Group>

      <Modal
        opened={modalOpen}
        onClose={() => setModalOpen(false)}
        title={`Search · ${targetEntity.name}`}
        size="lg"
        centered
        padding="md"
      >
        <TextInput
          label="Search"
          placeholder="Type at least 2 characters"
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          mb="sm"
          autoFocus
        />
        {debouncedTerm.length > 0 && debouncedTerm.length < 2 ? (
          <Text size="sm" c="dimmed">
            Enter at least 2 characters to search.
          </Text>
        ) : null}
        {lookupError ? (
          <Text size="sm" c="red" role="alert">
            {lookupError}
          </Text>
        ) : null}
        {lookupLoading ? (
          <Group gap="xs" py="md">
            <Loader size="sm" />
            <Text size="sm">Searching…</Text>
          </Group>
        ) : (
          <ScrollArea.Autosize mah={360}>
            <Table striped highlightOnHover withTableBorder>
              <Table.Thead>
                <Table.Tr>
                  {orderedColumnSlugs.length > 0 ? (
                    orderedColumnSlugs.map((s) => (
                      <Table.Th key={s}>{slugToLabel.get(s) ?? s}</Table.Th>
                    ))
                  ) : (
                    <Table.Th>Label</Table.Th>
                  )}
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {lookupItems.map((row) => (
                  <Table.Tr
                    key={row.recordId}
                    style={{ cursor: 'pointer' }}
                    onClick={() => pickRow(row.recordId)}
                  >
                    {orderedColumnSlugs.length > 0 ? (
                      orderedColumnSlugs.map((s) => (
                        <Table.Td key={s}>{formatCell(row.values[s])}</Table.Td>
                      ))
                    ) : (
                      <Table.Td>{row.displayLabel || '—'}</Table.Td>
                    )}
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
            {!lookupLoading && debouncedTerm.length >= 2 && lookupItems.length === 0 && !lookupError ? (
              <Text size="sm" c="dimmed" py="sm">
                No records found.
              </Text>
            ) : null}
          </ScrollArea.Autosize>
        )}
      </Modal>
    </>
  );
}

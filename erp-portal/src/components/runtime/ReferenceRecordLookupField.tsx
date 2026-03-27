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
  getRecord,
  isEntityStatusAssignmentDebugEnabled,
  listFields,
  listRecords,
  lookupRecords,
  type EntityFieldDto,
  type RecordDto,
} from '../../api/schemas';
import { useRecordFormRuntime } from './RecordFormRuntimeContext';
import {
  ENTITY_STATUS_ASSIGNMENT_ENTITY_SLUG,
  ENTITY_STATUS_ENTITY_SLUG,
} from '../../utils/entityStatusCatalog';
import {
  isEntityStatusReferenceTarget,
  looksLikeRecordUuid,
  readReferenceFieldConfig,
} from '../../utils/referenceFieldConfig';
import { buildReferenceRecordLabel, dedupeSlugsPreserveOrder } from '../../utils/referenceRecordDisplayLabel';

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

  /**
   * Pass host reference field id whenever known; the API resolves scope (field rows win, else entity
   * definition). Avoids an extra schema read and matches users who have records:read but not schema:read.
   */
  const assignedForEntityFieldId =
    statusRefTarget && assignedForEntityId && field.id?.trim() ? field.id.trim() : undefined;
  const targetEntity = cfg.targetEntitySlug ? entityBySlug[cfg.targetEntitySlug] : undefined;
  const targetEntityId = targetEntity?.id;
  const defaultDisplaySlug = targetEntity?.defaultDisplayFieldSlug ?? undefined;
  const columnSlugs = cfg.lookupDisplaySlugs;

  useEffect(() => {
    if (!isEntityStatusAssignmentDebugEnabled()) return;
    if ((field.fieldType || '').toLowerCase() !== 'reference') return;
    const issues: string[] = [];
    const slugNorm = (cfg.targetEntitySlug || '').trim().toLowerCase();
    if (slugNorm === ENTITY_STATUS_ASSIGNMENT_ENTITY_SLUG) {
      issues.push(
        `Wrong target: "${ENTITY_STATUS_ASSIGNMENT_ENTITY_SLUG}" is the assignment-configuration entity (join rows), not the status catalog. Edit the field and set target entity slug to "${ENTITY_STATUS_ENTITY_SLUG}" so the dropdown lists status records; allowed values still come from your assignments.`
      );
    } else if (!statusRefTarget) {
      issues.push(
        `Not an entity_status reference (targetEntitySlug is "${cfg.targetEntitySlug || ''}"; use "${ENTITY_STATUS_ENTITY_SLUG}" for a status picker with assignment scoping).`
      );
    }
    if (cfg.uiMode !== 'dropdown') {
      issues.push(
        `referenceUiMode is "${cfg.uiMode}" — dropdown batch (listRecords) only runs when UI mode is "dropdown". Search mode uses lookup API after you type 2+ chars in the modal.`
      );
    }
    if (!tenantId) issues.push('tenantId is missing (session / auth).');
    if (!hostEntityId?.trim() && statusRefTarget) {
      issues.push('hostEntityId is missing (RecordFormRuntimeProvider). assignedForEntityId will be omitted.');
    }
    if (!field.id?.trim() && statusRefTarget) {
      issues.push('field.id is missing — assignedForEntityFieldId will be omitted.');
    }
    if (!cfg.targetEntitySlug) issues.push('targetEntitySlug is missing in field config.');
    if (cfg.targetEntitySlug && !targetEntityId) {
      issues.push(
        `No entity definition in page map for slug "${cfg.targetEntitySlug}". buildEntityBySlugForReferenceFields only loads targets used on this entity; ensure listEntities() includes entity_status.`
      );
    }
    const willRunDropdownList =
      cfg.uiMode === 'dropdown' && !!tenantId && !!targetEntityId;
    console.log('[erpDebugEntityStatus] reference field snapshot (why you may see no listRecords logs)', {
      fieldSlug: slug,
      fieldId: field.id ?? null,
      targetEntitySlug: cfg.targetEntitySlug || null,
      statusRefTarget,
      uiMode: cfg.uiMode,
      tenantId: tenantId ?? null,
      hostEntityId: hostEntityId ?? null,
      assignedForEntityId: assignedForEntityId ?? null,
      assignedForEntityFieldId: assignedForEntityFieldId ?? null,
      targetEntityId: targetEntityId ?? null,
      willRunDropdownList,
      issues,
    });
  }, [
    field.fieldType,
    field.id,
    slug,
    cfg.targetEntitySlug,
    cfg.uiMode,
    statusRefTarget,
    tenantId,
    hostEntityId,
    targetEntityId,
    assignedForEntityId,
    assignedForEntityFieldId,
  ]);

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
    let cancelled = false;
    setDropdownLoading(true);
    setDropdownError(null);
    const recordListParams = {
      page: 1,
      pageSize: 200,
      assignedForEntityId: assignedForEntityId ?? undefined,
      assignedForEntityFieldId: assignedForEntityFieldId ?? undefined,
    };
    if (isEntityStatusAssignmentDebugEnabled() && statusRefTarget) {
      console.log('[erpDebugEntityStatus] reference dropdown → listRecords context', {
        note:
          'The server applies entity_status_assignment using assignedForEntityId (host entity def) and assignedForEntityFieldId (reference field def). The dropdown does not call …/entity-status-assignments.',
        fieldSlug: slug,
        fieldId: field.id ?? null,
        targetEntitySlug: cfg.targetEntitySlug,
        entity_statusMirrorEntityId: targetEntityId,
        ...recordListParams,
      });
    }
    void listRecords(tenantId, targetEntityId, recordListParams)
      .then((page) => {
        if (cancelled) return;
        if (isEntityStatusAssignmentDebugEnabled() && statusRefTarget) {
          console.log('[erpDebugEntityStatus] reference dropdown ← listRecords response', {
            fieldSlug: slug,
            itemCount: page.items.length,
            total: page.total,
          });
        }
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
  ]);

  useEffect(() => {
    if (!modalOpen || !tenantId || !targetEntityId) {
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
    if (isEntityStatusAssignmentDebugEnabled() && statusRefTarget) {
      console.log('[erpDebugEntityStatus] reference search → lookupRecords', {
        fieldSlug: slug,
        term: debouncedTerm,
        assignedForEntityId: assignedForEntityId ?? null,
        assignedForEntityFieldId: assignedForEntityFieldId ?? null,
      });
    }
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
      label: buildReferenceRecordLabel(rec, defaultDisplaySlug, columnSlugs),
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

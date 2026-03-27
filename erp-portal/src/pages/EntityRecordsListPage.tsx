import { Fragment, useCallback, useEffect, useMemo, useRef, useState, type CSSProperties } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import {
  ActionIcon,
  Anchor,
  Badge,
  Box,
  Button,
  Checkbox,
  Code,
  Group,
  Loader,
  Select,
  Stack,
  Table,
  Text,
  TextInput,
  Title,
  UnstyledButton,
} from '@mantine/core';
import {
  flexRender,
  getCoreRowModel,
  getExpandedRowModel,
  getSortedRowModel,
  useReactTable,
  type Column,
  type ColumnDef,
  type ExpandedState,
  type SortingState,
} from '@tanstack/react-table';
import {
  activeEntityFields,
  getEntity,
  getEntityStatusAssignments,
  getRecord,
  getRecordListView,
  listEntities,
  listFields,
  listRecordListViews,
  listRecords,
  queryRecords,
  deleteRecord,
  patchRecord,
  type EntityDto,
  type EntityFieldDto,
  type RecordDto,
  type RecordListViewDto,
  type RecordQueryFilterNode,
} from '../api/schemas';
import {
  type RecordListColumnDefinition,
  parseRecordListViewDefinition,
  RECORD_LIST_ROW_ID_SLUG,
} from '../utils/recordListViewDefinition';
import { useAuth } from '../auth/AuthProvider';
import { buildRecordSearchFilter } from '../utils/recordListSearch';
import { ENTITY_STATUS_ENTITY_SLUG } from '../utils/entityStatusCatalog';
import {
  buildRecordListSummaryDisplay,
  buildReferenceRecordLabel,
  collectReferenceResolutionTasks,
  entitiesBySlugLower,
} from '../utils/referenceRecordDisplayLabel';
import { looksLikeRecordUuid, readReferenceFieldConfig } from '../utils/referenceFieldConfig';

function shortId(id: string): string {
  return id.length > 10 ? `${id.slice(0, 8)}…` : id;
}

function displayForRecord(entity: EntityDto, row: RecordDto): string {
  const slug = entity.defaultDisplayFieldSlug?.trim();
  if (slug) {
    const v = row.values[slug];
    if (v !== undefined && v !== null && String(v) !== '') return String(v);
  }
  if (row.externalId) return row.externalId;
  const keys = Object.keys(row.values);
  if (keys.length > 0) {
    const v = row.values[keys[0]];
    if (v !== undefined && v !== null) return String(v);
  }
  return '—';
}

/** Basic list &quot;Display&quot; column: server scalar summary, else flagged fields (+ ref labels), else entity default display. */
function basicListDisplayText(
  entity: EntityDto | null,
  row: RecordDto,
  listSummaryFieldsOrdered: EntityFieldDto[],
  refLabels: Record<string, string>
): string {
  if (!entity) return '';
  const server = row.listSummaryDisplay?.trim();
  if (server) return server;
  if (listSummaryFieldsOrdered.length > 0) {
    return buildRecordListSummaryDisplay(row, listSummaryFieldsOrdered, refLabels);
  }
  return displayForRecord(entity, row);
}

const REF_LABEL_FETCH_CONCURRENCY = 8;

function chunkArray<T>(arr: T[], size: number): T[][] {
  const out: T[][] = [];
  for (let i = 0; i < arr.length; i += size) out.push(arr.slice(i, i + size));
  return out;
}

function parseCommaSlugs(raw: string | null): string[] {
  if (!raw || !raw.trim()) return [];
  return raw
    .split(',')
    .map((s) => s.trim())
    .filter((s) => s.length > 0);
}

function formatCellValue(
  row: RecordDto,
  slug: string,
  field?: EntityFieldDto,
  refLabels?: Record<string, string>
): string {
  const v = row.values[slug];
  if (v === undefined || v === null) return '—';
  const ft = field?.fieldType?.toLowerCase() ?? '';
  if (ft === 'reference') {
    const uuid = String(v).trim();
    const key = `${slug}::${uuid}`;
    const lbl = refLabels?.[key]?.trim();
    if (lbl) return lbl;
    if (looksLikeRecordUuid(uuid)) return shortId(uuid);
    return String(v);
  }
  if (ft === 'boolean') return v === true || v === 'true' ? 'Yes' : v === false || v === 'false' ? 'No' : String(v);
  if (typeof v === 'object') return JSON.stringify(v);
  return String(v);
}

/**
 * Value used for client-side sort on the current page (TanStack).
 * Reference columns still sort by stored UUID (raw value), while {@link formatCellValue} may show a resolved label.
 */
function sortValueForField(row: RecordDto, slug: string, field?: EntityFieldDto): string | number | null {
  const v = row.values[slug];
  if (v === undefined || v === null) return null;
  const ft = field?.fieldType?.toLowerCase() ?? '';
  if (ft === 'number') {
    const n = typeof v === 'number' ? v : Number(v);
    return Number.isFinite(n) ? n : String(v);
  }
  if (ft === 'boolean') {
    if (v === true || v === 'true') return 1;
    if (v === false || v === 'false') return 0;
    return String(v);
  }
  if (typeof v === 'object') return JSON.stringify(v);
  return String(v);
}

function RecordsListColumnHeader({
  column,
  label,
  align,
}: {
  column: Column<RecordDto, unknown>;
  label: string;
  align?: 'left' | 'center' | 'right';
}) {
  const sorted = column.getIsSorted();
  const justify = align === 'right' ? 'flex-end' : align === 'center' ? 'center' : 'flex-start';

  if (!column.getCanSort()) {
    return label ? (
      <Text fw={600} ta={align}>
        {label}
      </Text>
    ) : null;
  }

  return (
    <UnstyledButton type="button" onClick={column.getToggleSortingHandler()} style={{ width: '100%' }}>
      <Group gap={4} wrap="nowrap" justify={justify}>
        <Text span fw={600}>
          {label}
        </Text>
        <Text span size="xs" c="dimmed" aria-hidden>
          {sorted === 'asc' ? '↑' : sorted === 'desc' ? '↓' : '↕'}
        </Text>
      </Group>
    </UnstyledButton>
  );
}

const SEARCH_DEBOUNCE_MS = 400;

const VIEW_UUID_RE =
  /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/;

function columnWidthStyle(width: string | number | undefined): CSSProperties | undefined {
  if (width === undefined || width === null) return undefined;
  if (typeof width === 'number') return { width: width, maxWidth: width };
  if (width === 'narrow') return { width: 100, maxWidth: 120 };
  if (width === 'medium') return { width: 160, maxWidth: 200 };
  if (width === 'wide') return { width: 240, maxWidth: 320 };
  return undefined;
}

type RecordColMeta = {
  thStyle?: CSSProperties;
  tdStyle?: CSSProperties;
  slug?: string;
};

function buildEntityRecordColumns(p: {
  /** False until the first records load has finished resolving list view / fields for this entity (avoids flashing the basic Id/Display/audit columns before saved view columns apply). */
  listLayoutReady: boolean;
  showRecordIdColumn: boolean;
  useCustomLayout: boolean;
  visibleColSlugs: string[];
  showActions: boolean;
  fieldBySlug: Map<string, EntityFieldDto>;
  colMetaBySlug: Map<string, RecordListColumnDefinition>;
  inlineSlugs: Set<string>;
  canRecordsWrite: boolean;
  entity: EntityDto | null;
  entityId: string;
  /** Basic layout Display column: ordered fields with {@code includeInListSummaryDisplay}. */
  listSummaryFieldsOrdered: EntityFieldDto[];
  referenceLabelMap: Record<string, string>;
  saveInline: (row: RecordDto, slug: string, raw: unknown) => Promise<void>;
  onDelete: (row: RecordDto) => void | Promise<void>;
}): ColumnDef<RecordDto, unknown>[] {
  const cols: ColumnDef<RecordDto, unknown>[] = [];

  if (!p.listLayoutReady) {
    return cols;
  }

  if (p.showRecordIdColumn) {
    cols.push({
      id: 'rowId',
      accessorFn: (row) => row.id,
      header: ({ column }) => <RecordsListColumnHeader column={column} label="Id" />,
      cell: ({ row }) => <Code fz="sm">{shortId(row.original.id)}</Code>,
      sortingFn: 'alphanumeric',
    });
  }

  if (p.useCustomLayout) {
    for (const slug of p.visibleColSlugs) {
      const f = p.fieldBySlug.get(slug);
      const meta = p.colMetaBySlug.get(slug);
      const label = meta?.label?.trim() || f?.name || slug;
      const w = columnWidthStyle(meta?.width);
      const ta = meta?.align;
      const thStyle: CSSProperties = { ...w, ...(ta ? { textAlign: ta } : {}) };
      const tdStyle: CSSProperties = {
        ...columnWidthStyle(meta?.width),
        ...(meta?.align ? { textAlign: meta.align } : {}),
      };
      cols.push({
        id: `field-${slug}`,
        accessorFn: (row) => sortValueForField(row, slug, f),
        header: ({ column }) => (
          <RecordsListColumnHeader column={column} label={label} align={ta} />
        ),
        meta: { thStyle, tdStyle, slug } satisfies RecordColMeta,
        sortingFn: 'alphanumeric',
        cell: ({ row, column }) => {
          const slugInner = (column.columnDef.meta as RecordColMeta | undefined)?.slug ?? slug;
          const r = row.original;
          const field = p.fieldBySlug.get(slugInner);
          const colDefMeta = p.colMetaBySlug.get(slugInner);
          const inline = p.inlineSlugs.has(slugInner) && p.canRecordsWrite && field;
          if (inline && field) {
            const ft = field.fieldType.toLowerCase();
            const v = r.values[slugInner];
            const inputKey = `${r.id}-${slugInner}-${r.updatedAt ?? ''}`;
            if (ft === 'reference') {
              return <Text size="xs">{formatCellValue(r, slugInner, field, p.referenceLabelMap)}</Text>;
            }
            if (ft === 'boolean') {
              return (
                <Checkbox
                  checked={v === true || v === 'true'}
                  onChange={(e) => void p.saveInline(r, slugInner, e.currentTarget.checked)}
                />
              );
            }
            if (ft === 'number') {
              return (
                <TextInput
                  key={inputKey}
                  type="number"
                  size="xs"
                  w={140}
                  defaultValue={v === null || v === undefined ? '' : String(v)}
                  onBlur={(e) =>
                    void p.saveInline(r, slugInner, e.target.value === '' ? null : e.target.value)
                  }
                />
              );
            }
            return (
              <TextInput
                key={inputKey}
                size="xs"
                defaultValue={v === null || v === undefined ? '' : String(v)}
                onBlur={(e) => void p.saveInline(r, slugInner, e.target.value)}
              />
            );
          }
          const text = formatCellValue(r, slugInner, field, p.referenceLabelMap);
          const linkRec = colDefMeta?.linkToRecord;
          if (linkRec) {
            return (
              <Anchor component={Link} to={`/entities/${p.entityId}/records/${r.id}`} size="sm">
                {text}
              </Anchor>
            );
          }
          return text;
        },
      });
    }
  } else {
    cols.push(
      {
        id: 'display',
        accessorFn: (row) =>
          basicListDisplayText(p.entity, row, p.listSummaryFieldsOrdered, p.referenceLabelMap),
        header: ({ column }) => <RecordsListColumnHeader column={column} label="Display" />,
        cell: ({ row }) =>
          p.entity
            ? basicListDisplayText(p.entity, row.original, p.listSummaryFieldsOrdered, p.referenceLabelMap)
            : '—',
        sortingFn: 'alphanumeric',
      },
      {
        id: 'created',
        accessorFn: (row) => row.createdAt ?? '',
        header: ({ column }) => <RecordsListColumnHeader column={column} label="Created" />,
        cell: ({ row }) =>
          row.original.createdAt ? new Date(row.original.createdAt).toLocaleString() : '—',
        sortingFn: 'datetime',
      },
      {
        id: 'updated',
        accessorFn: (row) => row.updatedAt ?? '',
        header: ({ column }) => <RecordsListColumnHeader column={column} label="Updated" />,
        cell: ({ row }) =>
          row.original.updatedAt ? new Date(row.original.updatedAt).toLocaleString() : '—',
        sortingFn: 'datetime',
      },
      {
        id: 'createdBy',
        accessorFn: (row) =>
          row.createdByLabel?.trim() || row.createdBy || '',
        header: ({ column }) => <RecordsListColumnHeader column={column} label="Created by" />,
        cell: ({ row }) => {
          const r = row.original;
          return r.createdByLabel?.trim() ? (
            <span title={r.createdBy ?? undefined}>{r.createdByLabel.trim()}</span>
          ) : r.createdBy ? (
            <Code fz="sm" title={r.createdBy}>
              {shortId(r.createdBy)}
            </Code>
          ) : (
            '—'
          );
        },
        sortingFn: 'alphanumeric',
      },
      {
        id: 'updatedBy',
        accessorFn: (row) => row.updatedByLabel?.trim() || row.updatedBy || '',
        header: ({ column }) => (
          <RecordsListColumnHeader column={column} label="Last edited by" />
        ),
        cell: ({ row }) => {
          const r = row.original;
          return r.updatedByLabel?.trim() ? (
            <span title={r.updatedBy ?? undefined}>{r.updatedByLabel.trim()}</span>
          ) : r.updatedBy ? (
            <Code fz="sm" title={r.updatedBy}>
              {shortId(r.updatedBy)}
            </Code>
          ) : (
            '—'
          );
        },
        sortingFn: 'alphanumeric',
      }
    );
  }

  if (p.useCustomLayout) {
    cols.push(
      {
        id: 'aud-updated',
        accessorFn: (row) => row.updatedAt ?? '',
        header: ({ column }) => <RecordsListColumnHeader column={column} label="Updated" />,
        cell: ({ row }) =>
          row.original.updatedAt ? new Date(row.original.updatedAt).toLocaleString() : '—',
        sortingFn: 'datetime',
      },
      {
        id: 'aud-created',
        accessorFn: (row) => row.createdAt ?? '',
        header: ({ column }) => <RecordsListColumnHeader column={column} label="Created" />,
        cell: ({ row }) =>
          row.original.createdAt ? new Date(row.original.createdAt).toLocaleString() : '—',
        sortingFn: 'datetime',
      },
      {
        id: 'aud-createdBy',
        accessorFn: (row) => row.createdByLabel?.trim() || row.createdBy || '',
        header: ({ column }) => <RecordsListColumnHeader column={column} label="Created by" />,
        cell: ({ row }) => {
          const r = row.original;
          return r.createdByLabel?.trim() ? (
            <span title={r.createdBy ?? undefined}>{r.createdByLabel.trim()}</span>
          ) : r.createdBy ? (
            <Code fz="sm" title={r.createdBy}>
              {shortId(r.createdBy)}
            </Code>
          ) : (
            '—'
          );
        },
        sortingFn: 'alphanumeric',
      },
      {
        id: 'aud-updatedBy',
        accessorFn: (row) => row.updatedByLabel?.trim() || row.updatedBy || '',
        header: ({ column }) => (
          <RecordsListColumnHeader column={column} label="Last edited by" />
        ),
        cell: ({ row }) => {
          const r = row.original;
          return r.updatedByLabel?.trim() ? (
            <span title={r.updatedBy ?? undefined}>{r.updatedByLabel.trim()}</span>
          ) : r.updatedBy ? (
            <Code fz="sm" title={r.updatedBy}>
              {shortId(r.updatedBy)}
            </Code>
          ) : (
            '—'
          );
        },
        sortingFn: 'alphanumeric',
      }
    );
  }

  if (p.showActions) {
    cols.push({
      id: 'actions',
      enableSorting: false,
      header: '',
      cell: ({ row }) => {
        const r = row.original;
        return (
          <Group gap={4} wrap="nowrap">
            <Anchor component={Link} to={`/entities/${p.entityId}/records/${r.id}`} size="sm">
              Edit
            </Anchor>
            {p.canRecordsWrite && (
              <>
                <Text span size="sm" c="dimmed">
                  ·
                </Text>
                <UnstyledButton
                  type="button"
                  fz="sm"
                  c="var(--mantine-color-anchor)"
                  style={{ textDecoration: 'underline' }}
                  onClick={() => void p.onDelete(r)}
                >
                  Delete
                </UnstyledButton>
              </>
            )}
          </Group>
        );
      },
    });
  }

  return cols;
}

export function EntityRecordsListPage() {
  const { entityId = '' } = useParams();
  const [searchParams, setSearchParams] = useSearchParams();
  const { tenantId, canRecordsRead, canRecordsWrite, canCreatePortalNavItem, canSchemaWrite } = useAuth();

  const page = Math.max(1, parseInt(searchParams.get('page') || '1', 10) || 1);
  const pageSize = Math.min(200, Math.max(1, parseInt(searchParams.get('pageSize') || '50', 10) || 50));
  const qParam = (searchParams.get('q') ?? '').slice(0, 200);
  const viewIdParam = useMemo(() => {
    const v = (searchParams.get('view') ?? '').trim();
    return VIEW_UUID_RE.test(v) ? v : '';
  }, [searchParams]);
  const colsRaw = searchParams.get('cols') ?? '';
  const colsSlugs = useMemo(() => parseCommaSlugs(colsRaw), [colsRaw]);
  const inlineRaw = searchParams.get('inline') ?? '';
  const legacyCustomColumns = !viewIdParam && colsSlugs.length > 0;

  const [entity, setEntity] = useState<EntityDto | null>(null);
  const [fields, setFields] = useState<EntityFieldDto[] | null>(null);
  /**
   * Column config from entity-builder when URL has <code>?view=</code>, or when the entity has a default list view
   * (legacy <code>cols</code> in the URL still wins and skips default resolution).
   */
  const [savedListView, setSavedListView] = useState<{
    columns: RecordListColumnDefinition[];
    showRowActions: boolean;
    showRecordId: boolean;
  } | null>(null);
  /** Set when columns come from the default list view without <code>?view=</code> — used for “Edit list view”. */
  const [autoResolvedViewId, setAutoResolvedViewId] = useState<string | null>(null);
  /** Shown in the header; set whenever the grid uses a saved list view definition. */
  const [activeListViewInfo, setActiveListViewInfo] = useState<{
    id: string;
    name: string;
    isDefault: boolean;
  } | null>(null);
  /** For the list-view switcher (empty when <code>cols=</code> legacy mode). */
  const [listViewsBrief, setListViewsBrief] = useState<RecordListViewDto[]>([]);
  /** Set in `load` finally to `entityId` so we only render basic vs custom columns after list view + fields resolution (same entity). */
  const [layoutResolvedEntityId, setLayoutResolvedEntityId] = useState<string | null>(null);
  const listLayoutReady = layoutResolvedEntityId === entityId && entityId.length > 0;
  const [items, setItems] = useState<RecordDto[]>([]);
  const [total, setTotal] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [sorting, setSorting] = useState<SortingState>([]);
  const [expanded, setExpanded] = useState<ExpandedState>({});
  const [tenantEntities, setTenantEntities] = useState<EntityDto[] | null>(null);
  const [referenceLabelMap, setReferenceLabelMap] = useState<Record<string, string>>({});

  const statusFilterUuid = useMemo(() => (searchParams.get('ebStatus') ?? '').trim(), [searchParams]);
  const statusFilterFieldSlug = useMemo(() => (searchParams.get('ebStatusField') ?? '').trim(), [searchParams]);

  const [statusFilterOptions, setStatusFilterOptions] = useState<{ value: string; label: string }[]>([]);

  const [searchDraft, setSearchDraft] = useState(qParam);
  const searchDebounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  /** After we sync bare `/records` to `?view=&showRecordId=`, avoid repeating unless the user clears `view` again (e.g. picks “Default” in the switcher). */
  const defaultRecordsQuerySyncedRef = useRef(false);
  const prevViewIdParamRef = useRef<string>(viewIdParam);
  useEffect(() => {
    setSearchDraft(qParam);
  }, [qParam]);

  useEffect(() => {
    defaultRecordsQuerySyncedRef.current = false;
  }, [entityId]);

  useEffect(() => {
    const prev = prevViewIdParamRef.current;
    prevViewIdParamRef.current = viewIdParam;
    if (prev !== '' && viewIdParam === '') {
      defaultRecordsQuerySyncedRef.current = false;
    }
  }, [viewIdParam]);

  /** Bare `/entities/…/records` (no `view=`) uses the entity default list view; mirror that in the URL so portal links and bookmarks match the designer. */
  useEffect(() => {
    if (viewIdParam) return;
    if (colsSlugs.length > 0) return;
    if (!autoResolvedViewId || !savedListView) return;
    if (defaultRecordsQuerySyncedRef.current) return;

    defaultRecordsQuerySyncedRef.current = true;
    setSearchParams(
      (prev) => {
        const n = new URLSearchParams(prev);
        n.set('view', autoResolvedViewId);
        if (!savedListView.showRecordId) n.set('showRecordId', '0');
        else n.delete('showRecordId');
        if (!savedListView.showRowActions) n.set('actions', '0');
        else n.delete('actions');
        return n;
      },
      { replace: true }
    );
  }, [viewIdParam, colsSlugs.length, autoResolvedViewId, savedListView, setSearchParams]);

  useEffect(() => {
    if (searchDebounceRef.current) clearTimeout(searchDebounceRef.current);
    searchDebounceRef.current = setTimeout(() => {
      const next = searchDraft.slice(0, 200);
      if (next === qParam) return;
      setSearchParams(
        (prev) => {
          const n = new URLSearchParams(prev);
          if (!next.trim()) n.delete('q');
          else n.set('q', next.trim());
          n.set('page', '1');
          return n;
        },
        { replace: true }
      );
    }, SEARCH_DEBOUNCE_MS);
    return () => {
      if (searchDebounceRef.current) clearTimeout(searchDebounceRef.current);
    };
  }, [searchDraft, qParam, setSearchParams]);

  useEffect(() => {
    let cancelled = false;
    void listEntities()
      .then((rows) => {
        if (!cancelled) setTenantEntities(rows);
      })
      .catch(() => {
        if (!cancelled) setTenantEntities([]);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const fieldBySlug = useMemo(() => {
    const m = new Map<string, EntityFieldDto>();
    for (const f of fields ?? []) m.set(f.slug, f);
    return m;
  }, [fields]);

  const listSummaryFieldsOrdered = useMemo(() => {
    if (!fields) return [];
    const active = activeEntityFields(fields).filter((f) => f.includeInListSummaryDisplay);
    return [...active].sort((a, b) => {
      if (a.sortOrder !== b.sortOrder) return a.sortOrder - b.sortOrder;
      return a.name.localeCompare(b.name);
    });
  }, [fields]);

  const entityStatusRefFields = useMemo(() => {
    if (!fields) return [];
    return fields.filter((f) => {
      if (f.fieldType?.toLowerCase() !== 'reference') return false;
      return readReferenceFieldConfig(f.config).targetEntitySlug === ENTITY_STATUS_ENTITY_SLUG;
    });
  }, [fields]);

  useEffect(() => {
    if (!tenantId || !entityId || entityStatusRefFields.length === 0) {
      setStatusFilterOptions([]);
      return;
    }
    let cancelled = false;
    void getEntityStatusAssignments(tenantId, entityId)
      .then((rows) => {
        if (cancelled) return;
        setStatusFilterOptions(rows.map((r) => ({ value: r.entityStatusId, label: `${r.code} — ${r.label}` })));
      })
      .catch(() => {
        if (!cancelled) setStatusFilterOptions([]);
      });
    return () => {
      cancelled = true;
    };
  }, [tenantId, entityId, entityStatusRefFields.length]);

  const visibleColSlugs = useMemo(() => {
    const skipPk = (s: string) => s.trim().toLowerCase() !== RECORD_LIST_ROW_ID_SLUG;
    if (savedListView) {
      return savedListView.columns.map((c) => c.fieldSlug).filter((s) => fieldBySlug.has(s) && skipPk(s));
    }
    if (!legacyCustomColumns) return [];
    return colsSlugs.filter((s) => fieldBySlug.has(s) && skipPk(s));
  }, [savedListView, legacyCustomColumns, colsSlugs, fieldBySlug]);

  const entitiesBySlug = useMemo(() => entitiesBySlugLower(tenantEntities ?? []), [tenantEntities]);

  const slugsForRefResolution = useMemo(() => {
    const s = new Set<string>();
    for (const x of visibleColSlugs) s.add(x);
    for (const f of listSummaryFieldsOrdered) s.add(f.slug);
    return s;
  }, [visibleColSlugs, listSummaryFieldsOrdered]);

  const referenceResolutionTasks = useMemo(
    () => collectReferenceResolutionTasks(fields ?? [], slugsForRefResolution, entitiesBySlug),
    [fields, slugsForRefResolution, entitiesBySlug]
  );

  const colMetaBySlug = useMemo(() => {
    const m = new Map<string, RecordListColumnDefinition>();
    if (savedListView) {
      for (const c of savedListView.columns) m.set(c.fieldSlug, c);
    }
    return m;
  }, [savedListView]);

  const inlineSlugs = useMemo(() => {
    if (savedListView) {
      return new Set(
        savedListView.columns
          .filter((c) => c.inlineEditable && c.fieldSlug.trim().toLowerCase() !== RECORD_LIST_ROW_ID_SLUG)
          .map((c) => c.fieldSlug)
      );
    }
    return new Set(
      parseCommaSlugs(inlineRaw).filter((s) => s.trim().toLowerCase() !== RECORD_LIST_ROW_ID_SLUG)
    );
  }, [savedListView, inlineRaw]);

  const showActions = savedListView ? savedListView.showRowActions : searchParams.get('actions') !== '0';

  const useCustomLayout = visibleColSlugs.length > 0 && (savedListView !== null || legacyCustomColumns);

  /** Explicit `showRecordId` query wins, then saved view definition, else show Id (basic / legacy). */
  const showRecordIdColumn = (() => {
    const q = (searchParams.get('showRecordId') ?? searchParams.get('showUuid') ?? '').toLowerCase();
    if (q === '0' || q === 'false') return false;
    if (q === '1' || q === 'true') return true;
    if (savedListView != null) return savedListView.showRecordId;
    return true;
  })();

  useEffect(() => {
    if (!tenantId || !fields?.length || items.length === 0 || referenceResolutionTasks.length === 0) {
      setReferenceLabelMap({});
      return;
    }
    let cancelled = false;
    const entitiesMap = entitiesBySlug;

    const fetchKeys = new Map<string, { targetEntityId: string; uuid: string }>();
    for (const row of items) {
      for (const { field, targetEntityId } of referenceResolutionTasks) {
        const raw = row.values[field.slug];
        if (raw === undefined || raw === null) continue;
        const uuid = String(raw).trim();
        if (!looksLikeRecordUuid(uuid)) continue;
        fetchKeys.set(`${targetEntityId}::${uuid}`, { targetEntityId, uuid });
      }
    }

    if (fetchKeys.size === 0) {
      setReferenceLabelMap({});
      return;
    }

    async function run() {
      const recordCache = new Map<string, RecordDto>();
      const pairs = [...fetchKeys.values()];
      for (const group of chunkArray(pairs, REF_LABEL_FETCH_CONCURRENCY)) {
        if (cancelled) return;
        await Promise.all(
          group.map(async ({ targetEntityId, uuid }) => {
            const k = `${targetEntityId}::${uuid}`;
            try {
              const rec = await getRecord(tenantId, targetEntityId, uuid);
              if (!cancelled) recordCache.set(k, rec);
            } catch {
              /* leave missing; UI falls back to short UUID */
            }
          })
        );
      }
      if (cancelled) return;
      const next: Record<string, string> = {};
      for (const row of items) {
        for (const { field, targetEntityId } of referenceResolutionTasks) {
          const raw = row.values[field.slug];
          if (raw === undefined || raw === null) continue;
          const uuid = String(raw).trim();
          if (!looksLikeRecordUuid(uuid)) continue;
          const rec = recordCache.get(`${targetEntityId}::${uuid}`);
          if (!rec) continue;
          const cfg = readReferenceFieldConfig(field.config);
          const ent = entitiesMap[cfg.targetEntitySlug];
          next[`${field.slug}::${uuid}`] = buildReferenceRecordLabel(
            rec,
            ent?.defaultDisplayFieldSlug,
            cfg.lookupDisplaySlugs
          );
        }
      }
      setReferenceLabelMap(next);
    }

    void run();
    return () => {
      cancelled = true;
    };
  }, [tenantId, items, fields, referenceResolutionTasks, entitiesBySlug]);

  const load = useCallback(async () => {
    if (!tenantId || !entityId || !canRecordsRead) return;
    setError(null);
    setBusy(true);
    try {
      const useSavedViews = colsSlugs.length === 0;
      const [e, flds, viewsForPicker] = await Promise.all([
        getEntity(entityId),
        listFields(entityId),
        useSavedViews ? listRecordListViews(entityId) : Promise.resolve([] as RecordListViewDto[]),
      ]);
      setEntity(e);
      setFields(flds);
      setListViewsBrief(viewsForPicker);

      const bySlug = new Map(flds.map((f) => [f.slug, f]));

      let viewCols: RecordListColumnDefinition[] = [];
      let appliedSavedListView = false;

      if (viewIdParam) {
        setAutoResolvedViewId(null);
        const dto = await getRecordListView(entityId, viewIdParam);
        const def = parseRecordListViewDefinition(dto.definition);
        if (!def) {
          throw new Error('List view definition is missing or not version 1');
        }
        viewCols = def.columns.filter((c) => c.visible !== false);
        setSavedListView({
          columns: viewCols,
          showRowActions: def.showRowActions !== false,
          showRecordId: def.showRecordId !== false,
        });
        setActiveListViewInfo({ id: dto.id, name: dto.name, isDefault: dto.isDefault });
        appliedSavedListView = true;
      } else if (colsSlugs.length > 0) {
        setSavedListView(null);
        setAutoResolvedViewId(null);
        setActiveListViewInfo(null);
      } else {
        const defMeta = viewsForPicker.find((v) => v.isDefault);
        if (!defMeta) {
          setSavedListView(null);
          setAutoResolvedViewId(null);
          setActiveListViewInfo(null);
        } else {
          const dto = await getRecordListView(entityId, defMeta.id);
          const def = parseRecordListViewDefinition(dto.definition);
          if (!def) {
            setSavedListView(null);
            setAutoResolvedViewId(null);
            setActiveListViewInfo(null);
          } else {
            viewCols = def.columns.filter((c) => c.visible !== false);
            setSavedListView({
              columns: viewCols,
              showRowActions: def.showRowActions !== false,
              showRecordId: def.showRecordId !== false,
            });
            setAutoResolvedViewId(defMeta.id);
            setActiveListViewInfo({ id: dto.id, name: dto.name, isDefault: dto.isDefault });
            appliedSavedListView = true;
          }
        }
      }

      let restrictSearch: string[] | null = null;
      const searchableSlug = (s: string) =>
        bySlug.has(s) && s.trim().toLowerCase() !== RECORD_LIST_ROW_ID_SLUG;
      if (appliedSavedListView) {
        const vis = viewCols.map((c) => c.fieldSlug).filter(searchableSlug);
        restrictSearch = vis.length > 0 ? vis : null;
      } else {
        const parsedCols = parseCommaSlugs(colsRaw);
        const visible = parsedCols.filter(searchableSlug);
        restrictSearch = parsedCols.length > 0 && visible.length > 0 ? visible : null;
      }
      const textFilter = buildRecordSearchFilter(flds, qParam, restrictSearch);

      const refToEntityStatus = flds.filter((f) => {
        if (f.fieldType?.toLowerCase() !== 'reference') return false;
        return readReferenceFieldConfig(f.config).targetEntitySlug === ENTITY_STATUS_ENTITY_SLUG;
      });
      let effectiveStatusSlug: string | null = null;
      if (statusFilterUuid && looksLikeRecordUuid(statusFilterUuid)) {
        if (statusFilterFieldSlug && refToEntityStatus.some((r) => r.slug === statusFilterFieldSlug)) {
          effectiveStatusSlug = statusFilterFieldSlug;
        } else if (refToEntityStatus.length === 1) {
          effectiveStatusSlug = refToEntityStatus[0].slug;
        }
      }

      let mergedFilter: RecordQueryFilterNode | null = textFilter;
      if (effectiveStatusSlug) {
        const statusClause: RecordQueryFilterNode = {
          op: 'eq',
          field: effectiveStatusSlug,
          value: statusFilterUuid,
        };
        mergedFilter = textFilter ? { op: 'and', children: [textFilter, statusClause] } : statusClause;
      }

      if (mergedFilter) {
        const pr = await queryRecords(tenantId, entityId, { filter: mergedFilter, page, pageSize });
        setItems(pr.items);
        setTotal(pr.total);
      } else {
        const pr = await listRecords(tenantId, entityId, { page, pageSize });
        setItems(pr.items);
        setTotal(pr.total);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load');
      setEntity(null);
      setFields(null);
      setItems([]);
      setTotal(0);
      setSavedListView(null);
      setAutoResolvedViewId(null);
      setActiveListViewInfo(null);
      setListViewsBrief([]);
    } finally {
      setLayoutResolvedEntityId(entityId);
      setBusy(false);
    }
  }, [
    tenantId,
    entityId,
    canRecordsRead,
    page,
    pageSize,
    qParam,
    colsRaw,
    colsSlugs.length,
    viewIdParam,
    statusFilterUuid,
    statusFilterFieldSlug,
  ]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    setSorting([]);
    setExpanded({});
  }, [page, pageSize, entityId]);

  function setPageParams(nextPage: number) {
    setSearchParams(
      (prev) => {
        const n = new URLSearchParams(prev);
        n.set('page', String(Math.max(1, nextPage)));
        return n;
      },
      { replace: true }
    );
  }

  function setPageSizeParams(nextSize: number) {
    const sz = Math.min(200, Math.max(1, nextSize));
    setSearchParams(
      (prev) => {
        const n = new URLSearchParams(prev);
        n.set('pageSize', String(sz));
        n.set('page', '1');
        return n;
      },
      { replace: true }
    );
  }

  const onDelete = useCallback(
    async (row: RecordDto) => {
      if (!tenantId || !canRecordsWrite) return;
      if (!window.confirm(`Delete record ${shortId(row.id)}?`)) return;
      try {
        await deleteRecord(tenantId, entityId, row.id);
        await load();
      } catch (e) {
        window.alert(e instanceof Error ? e.message : 'Delete failed');
      }
    },
    [tenantId, entityId, canRecordsWrite, load]
  );

  const saveInline = useCallback(
    async (row: RecordDto, slug: string, raw: unknown) => {
      if (!tenantId || !canRecordsWrite) return;
      const field = fieldBySlug.get(slug);
      const ft = field?.fieldType?.toLowerCase() ?? 'string';
      let value: unknown = raw;
      if (ft === 'number') {
        if (raw === '' || raw === null || raw === undefined) value = null;
        else value = Number(raw);
      } else if (ft === 'boolean') value = Boolean(raw);
      else value = raw === '' ? null : raw;
      try {
        const updated = await patchRecord(tenantId, entityId, row.id, { values: { [slug]: value } });
        setItems((prev) => prev.map((r) => (r.id === row.id ? updated : r)));
      } catch (e) {
        window.alert(e instanceof Error ? e.message : 'Save failed');
        await load();
      }
    },
    [tenantId, entityId, canRecordsWrite, fieldBySlug, load]
  );

  const columns = useMemo(() => {
    const expandCol: ColumnDef<RecordDto, unknown> = {
      id: '_expand',
      enableSorting: false,
      header: '',
      meta: {
        thStyle: { width: 44, maxWidth: 44, textAlign: 'center' },
        tdStyle: { width: 44, maxWidth: 44, textAlign: 'center', verticalAlign: 'middle' },
      } satisfies RecordColMeta,
      cell: ({ row }) => (
        <ActionIcon
          variant="subtle"
          size="sm"
          aria-expanded={row.getIsExpanded()}
          aria-label={row.getIsExpanded() ? 'Collapse row details' : 'Expand row details'}
          onClick={row.getToggleExpandedHandler()}
        >
          <Text span fz="xs" fw={700} aria-hidden>
            {row.getIsExpanded() ? '▼' : '▶'}
          </Text>
        </ActionIcon>
      ),
    };
    return [
      expandCol,
      ...buildEntityRecordColumns({
        listLayoutReady,
        showRecordIdColumn,
        useCustomLayout,
        visibleColSlugs,
        showActions,
        fieldBySlug,
        colMetaBySlug,
        inlineSlugs,
        canRecordsWrite,
        entity,
        entityId,
        listSummaryFieldsOrdered,
        referenceLabelMap,
        saveInline,
        onDelete,
      }),
    ];
  }, [
    listLayoutReady,
    showRecordIdColumn,
    useCustomLayout,
    visibleColSlugs,
    showActions,
    fieldBySlug,
    colMetaBySlug,
    inlineSlugs,
    canRecordsWrite,
    entity,
    entityId,
    listSummaryFieldsOrdered,
    referenceLabelMap,
    saveInline,
    onDelete,
  ]);

  const table = useReactTable({
    data: items,
    columns,
    state: { sorting, expanded },
    onSortingChange: setSorting,
    onExpandedChange: setExpanded,
    getRowId: (row) => row.id,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    getExpandedRowModel: getExpandedRowModel(),
    getRowCanExpand: () => true,
    sortDescFirst: false,
  });

  if (!tenantId) {
    return (
      <div className="page-shell">
        <p role="alert" className="text-error">
          Missing <code>tenant_id</code> in access token — cannot load tenant-scoped records.
        </p>
      </div>
    );
  }

  if (!canRecordsRead) {
    return (
      <div className="page-shell">
        <p role="alert" className="text-error">
          Missing <code>entity_builder:records:read</code> permission.
        </p>
      </div>
    );
  }

  const totalPages = Math.max(1, Math.ceil(total / pageSize));

  return (
    <Stack gap="lg" className="page-shell page-shell-wide" maw={1400}>
      <Group gap="xs" wrap="wrap">
        <Anchor component={Link} to="/entities" size="sm">
          Entities
        </Anchor>
        <Text span c="dimmed" aria-hidden>
          /
        </Text>
        <Anchor component={Link} to={`/entities/${entityId}/layouts`} size="sm">
          {entity?.name ?? '…'}
        </Anchor>
        <Text span c="dimmed" aria-hidden>
          /
        </Text>
        <Text span size="sm">
          Records
        </Text>
      </Group>

      <Group justify="space-between" align="flex-start" wrap="wrap" gap="md">
        <Stack gap={4} maw={720}>
          <Title order={1} size="h2">
            Records
          </Title>
          <Text c="dimmed" size="sm">
            {entity ? `${entity.name} — browse and open records.` : 'Loading…'}
          </Text>
          {entity && (
            <Box component="div" mt="xs" fz="sm" c="dimmed">
              {legacyCustomColumns && (
                <>
                  Active list: <strong>Custom columns</strong> from this page&apos;s link (<code>cols=</code>). Switching
                  saved views is disabled until that parameter is removed.
                </>
              )}
              {!legacyCustomColumns && activeListViewInfo && (
                <>
                  Active list view: <strong>{activeListViewInfo.name}</strong>
                  {activeListViewInfo.isDefault && (
                    <Badge ml="xs" size="sm" variant="light" color="green" component="span">
                      default
                    </Badge>
                  )}
                  <span style={{ marginLeft: 8 }}>
                    {viewIdParam
                      ? '— URL includes list view id plus column options (e.g. showRecordId for the UUID column).'
                      : '— default list view: the address bar is updated with matching query params for sharing.'}
                  </span>
                </>
              )}
              {!legacyCustomColumns && !activeListViewInfo && (
                <>
                  Active list: <strong>Basic table</strong> (Id, display field, updated). Use{' '}
                  <code>&amp;showRecordId=0</code> (or <code>=1</code>) to override UUID column visibility vs. the saved
                  list view. Create list views under Form layouts; the one marked{' '}
                  <Badge size="xs" variant="light" color="green" component="span">
                    default
                  </Badge>{' '}
                  loads here automatically.
                </>
              )}
            </Box>
          )}
        </Stack>
        <Group gap="xs" wrap="nowrap" style={{ maxWidth: '100%', overflowX: 'auto' }}>
          {canRecordsWrite && (
            <Button component={Link} to={`/entities/${entityId}/records/new`} size="sm">
              Add record
            </Button>
          )}
          {(canCreatePortalNavItem || canSchemaWrite) && (
            <Button
              component={Link}
              to={
                viewIdParam || autoResolvedViewId
                  ? `/entities/${entityId}/list-views/${viewIdParam || autoResolvedViewId}`
                  : `/entities/${entityId}/list-views/new`
              }
              state={{ fromRecordsQuery: searchParams.toString() }}
              variant="default"
              size="sm"
            >
              Edit list view
            </Button>
          )}
          <Button component={Link} to={`/entities/${entityId}/layouts#record-list-views`} variant="default" size="sm">
            Form layouts
          </Button>
          <Button component={Link} to={`/entities/${entityId}/audit`} variant="default" size="sm">
            Activity & audit
          </Button>
        </Group>
      </Group>

      <Group align="flex-end" wrap="wrap" gap="md">
        <TextInput
          label="Search"
          placeholder="Filter rows (text fields)…"
          value={searchDraft}
          onChange={(e) => setSearchDraft(e.target.value.slice(0, 200))}
          aria-label="Search records"
          style={{ flex: '1 1 240px', minWidth: 200 }}
          size="sm"
        />
        {entityStatusRefFields.length > 0 && statusFilterOptions.length > 0 && (
          <>
            {entityStatusRefFields.length > 1 && (
              <Select
                label="Status field"
                aria-label="Status field to filter"
                size="sm"
                miw={160}
                data={entityStatusRefFields.map((f) => ({
                  value: f.slug,
                  label: f.displayLabel?.trim() || f.labelOverride?.trim() || f.name || f.slug,
                }))}
                value={
                  (statusFilterFieldSlug &&
                    entityStatusRefFields.some((f) => f.slug === statusFilterFieldSlug) &&
                    statusFilterFieldSlug) ||
                  entityStatusRefFields[0]?.slug
                }
                onChange={(slug) => {
                  if (!slug) return;
                  setSearchParams(
                    (prev) => {
                      const n = new URLSearchParams(prev);
                      n.set('ebStatusField', slug);
                      n.delete('ebStatus');
                      n.set('page', '1');
                      return n;
                    },
                    { replace: true }
                  );
                }}
              />
            )}
            <Select
              label="Status"
              aria-label="Filter by entity status"
              size="sm"
              miw={200}
              clearable
              placeholder="Any status"
              data={statusFilterOptions}
              value={statusFilterUuid || null}
              onChange={(next) => {
                setSearchParams(
                  (prev) => {
                    const n = new URLSearchParams(prev);
                    if (!next) {
                      n.delete('ebStatus');
                    } else {
                      n.set('ebStatus', next);
                      if (entityStatusRefFields.length > 1) {
                        const fieldSlug =
                          (statusFilterFieldSlug &&
                            entityStatusRefFields.some((f) => f.slug === statusFilterFieldSlug) &&
                            statusFilterFieldSlug) ||
                          entityStatusRefFields[0]?.slug;
                        if (fieldSlug) n.set('ebStatusField', fieldSlug);
                      }
                    }
                    n.set('page', '1');
                    return n;
                  },
                  { replace: true }
                );
              }}
            />
          </>
        )}
        <Select
          label="Page size"
          aria-label="Page size"
          size="sm"
          w={100}
          data={['25', '50', '100', '200']}
          value={String(pageSize)}
          onChange={(v) => v && setPageSizeParams(parseInt(v, 10))}
        />
        {!legacyCustomColumns && listViewsBrief.length > 0 && (
          <Select
            label="Switch list view"
            aria-label="Switch list view"
            size="sm"
            miw={200}
            data={[
              {
                value: '',
                label: listViewsBrief.some((v) => v.isDefault)
                  ? `Default — ${listViewsBrief.find((v) => v.isDefault)?.name ?? '…'}`
                  : 'Basic table',
              },
              ...listViewsBrief.map((v) => ({
                value: v.id,
                label: `${v.name}${v.isDefault ? ' ★' : ''}`,
              })),
            ]}
            value={viewIdParam || ''}
            onChange={(next) => {
              setSearchParams(
                (prev) => {
                  const n = new URLSearchParams(prev);
                  if (!next) n.delete('view');
                  else n.set('view', next);
                  n.set('page', '1');
                  return n;
                },
                { replace: true }
              );
            }}
          />
        )}
      </Group>

      {error && (
        <Text role="alert" c="red" size="sm">
          {error}
        </Text>
      )}
      {busy && (
        <Group gap="xs">
          <Loader size="sm" />
          <Text size="sm" c="dimmed">
            Loading…
          </Text>
        </Group>
      )}

      <Text size="xs" c="dimmed">
        Sort column headers to order <strong>this page</strong> of results. Expand a row (▶) to see all field values as
        JSON and a link to the full record.
      </Text>

      <Table.ScrollContainer minWidth={500} type="scroll">
        <Table striped highlightOnHover withTableBorder withColumnBorders>
          <Table.Thead>
            {table.getHeaderGroups().map((hg) => (
              <Table.Tr key={hg.id}>
                {hg.headers.map((header) => {
                  const m = header.column.columnDef.meta as RecordColMeta | undefined;
                  return (
                    <Table.Th key={header.id} style={m?.thStyle}>
                      {header.isPlaceholder
                        ? null
                        : flexRender(header.column.columnDef.header, header.getContext())}
                    </Table.Th>
                  );
                })}
              </Table.Tr>
            ))}
          </Table.Thead>
          <Table.Tbody>
            {table.getRowModel().rows.map((row) => (
              <Fragment key={row.id}>
                <Table.Tr>
                  {row.getVisibleCells().map((cell) => {
                    const m = cell.column.columnDef.meta as RecordColMeta | undefined;
                    return (
                      <Table.Td key={cell.id} style={m?.tdStyle}>
                        {flexRender(cell.column.columnDef.cell, cell.getContext())}
                      </Table.Td>
                    );
                  })}
                </Table.Tr>
                {row.getIsExpanded() && (
                  <Table.Tr>
                    <Table.Td colSpan={row.getVisibleCells().length} p={0}>
                      <Box
                        p="md"
                        style={{
                          background: 'var(--mantine-color-gray-0)',
                          borderTop: '1px solid var(--mantine-color-gray-3)',
                        }}
                      >
                        <Stack gap="sm">
                          <Group justify="space-between" wrap="wrap" gap="sm">
                            <Text size="sm" fw={600}>
                              All field values
                            </Text>
                            <Anchor component={Link} to={`/entities/${entityId}/records/${row.original.id}`} size="sm">
                              Open record
                            </Anchor>
                          </Group>
                          <Code
                            block
                            fz="xs"
                            style={{
                              whiteSpace: 'pre-wrap',
                              wordBreak: 'break-word',
                              maxHeight: 280,
                              overflow: 'auto',
                            }}
                          >
                            {JSON.stringify(row.original.values, null, 2)}
                          </Code>
                        </Stack>
                      </Box>
                    </Table.Td>
                  </Table.Tr>
                )}
              </Fragment>
            ))}
          </Table.Tbody>
        </Table>
      </Table.ScrollContainer>

      {items.length === 0 && !busy && !error && (
        <Text size="sm" c="dimmed">
          No records yet.{canRecordsWrite ? ' Use Add record to create one.' : ''}
        </Text>
      )}
      {totalPages > 1 && (
        <Group gap="md" wrap="wrap">
          <Button
            type="button"
            variant="default"
            size="xs"
            disabled={page <= 1 || busy}
            onClick={() => setPageParams(page - 1)}
          >
            Previous
          </Button>
          <Text size="sm" c="dimmed">
            Page {page} of {totalPages} ({total} total)
          </Text>
          <Button
            type="button"
            variant="default"
            size="xs"
            disabled={page >= totalPages || busy}
            onClick={() => setPageParams(page + 1)}
          >
            Next
          </Button>
        </Group>
      )}
    </Stack>
  );
}

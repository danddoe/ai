import { useCallback, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Button,
  Checkbox,
  Code,
  Select,
  Stack,
  Text,
  Textarea,
  TextInput,
  Title,
} from '@mantine/core';
import type { ColumnDef } from '@tanstack/react-table';
import {
  applyDdlImport,
  previewDdlImport,
  type DdlFieldPreviewDto,
  type DdlImportApplyResponse,
  type DdlImportPreviewResponse,
  type DdlRelationshipPreviewDto,
  ApiHttpError,
} from '../api/schemas';
import { useAuth } from '../auth/AuthProvider';
import { DataTable } from '../components/DataTable';
import { formatCategoryKeyLabel, PORTAL_NAV_CATEGORY_KEYS } from '../utils/portalNavCategoryKeys';

const DDL_FIELD_COLUMNS: ColumnDef<DdlFieldPreviewDto>[] = [
  { accessorKey: 'columnName', header: 'Column' },
  {
    accessorKey: 'fieldSlug',
    header: 'Slug',
    cell: ({ getValue }) => <Code fz="sm">{String(getValue())}</Code>,
  },
  {
    id: 'sqlType',
    header: 'SQL type',
    cell: ({ row }) => row.original.sqlDataType || '—',
  },
  { accessorKey: 'proposedFieldType', header: 'Field type' },
  {
    id: 'required',
    header: 'Required',
    cell: ({ row }) => (row.original.required ? 'yes' : 'no'),
  },
  {
    id: 'pk',
    header: 'PK',
    cell: ({ row }) => (row.original.primaryKey ? 'yes' : 'no'),
  },
  {
    id: 'fk',
    header: 'FK target',
    cell: ({ row }) => row.original.fkTargetEntitySlug ?? row.original.fkTargetTable ?? '—',
  },
  {
    id: 'resolvable',
    header: 'Resolvable',
    cell: ({ row }) =>
      row.original.targetEntityResolvable == null
        ? '—'
        : row.original.targetEntityResolvable
          ? 'yes'
          : 'no',
  },
  {
    id: 'warnings',
    header: 'Warnings',
    cell: ({ row }) => (row.original.warnings?.length ? row.original.warnings.join(', ') : '—'),
  },
];

const DDL_REL_COLUMNS: ColumnDef<DdlRelationshipPreviewDto>[] = [
  {
    accessorKey: 'relationshipSlug',
    header: 'Slug',
    cell: ({ getValue }) => <Code fz="sm">{String(getValue())}</Code>,
  },
  { accessorKey: 'name', header: 'Name' },
  {
    id: 'fromTo',
    header: 'From → to',
    cell: ({ row }) => (
      <>
        <Code fz="sm">{row.original.fromEntitySlug}</Code> → <Code fz="sm">{row.original.toEntitySlug}</Code> (
        {row.original.cardinality})
      </>
    ),
  },
  {
    id: 'creatable',
    header: 'Creatable',
    cell: ({ row }) => (row.original.creatableAfterImport ? 'yes' : 'no'),
  },
  {
    id: 'skip',
    header: 'Skip reason',
    cell: ({ row }) => row.original.skipReason ?? '—',
  },
];

function parseSkipSlugs(raw: string): string[] {
  return raw
    .split(/[,;\n]+/)
    .map((s) => s.trim())
    .filter(Boolean);
}

function buildRequestBody(params: {
  ddl: string;
  storageMode: string;
  coreBindingService: string;
  excludeAuditColumns: boolean;
  createRelationshipsFromForeignKeys: boolean;
  categoryKey: string;
  skipColumnSlugs: string;
}) {
  const skipColumnSlugs = parseSkipSlugs(params.skipColumnSlugs);
  return {
    ddl: params.ddl,
    storageMode: params.storageMode,
    coreBindingService: params.coreBindingService.trim() || undefined,
    excludeAuditColumns: params.excludeAuditColumns,
    createRelationshipsFromForeignKeys: params.createRelationshipsFromForeignKeys,
    skipColumnSlugs: skipColumnSlugs.length ? skipColumnSlugs : undefined,
    categoryKey: params.categoryKey.trim() || undefined,
  };
}

export function DdlImportPage() {
  const { canSchemaRead, canSchemaWrite, canPlatformSchemaWrite } = useAuth();
  const [ddl, setDdl] = useState('');
  const [storageMode, setStorageMode] = useState<'EAV_EXTENSION' | 'CORE_DOMAIN'>('EAV_EXTENSION');
  const [coreBindingService, setCoreBindingService] = useState('');
  const [excludeAuditColumns, setExcludeAuditColumns] = useState(true);
  const [createRelationshipsFromForeignKeys, setCreateRelationshipsFromForeignKeys] = useState(false);
  const [categoryKey, setCategoryKey] = useState('');
  const [skipColumnSlugs, setSkipColumnSlugs] = useState('');
  const [preview, setPreview] = useState<DdlImportPreviewResponse | null>(null);
  const [applyResult, setApplyResult] = useState<DdlImportApplyResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [previewBusy, setPreviewBusy] = useState(false);
  const [applyBusy, setApplyBusy] = useState(false);

  const categoryKeyOptions = useMemo(
    () => [...PORTAL_NAV_CATEGORY_KEYS].sort((a, b) => a.localeCompare(b)),
    []
  );

  const bodyBase = useMemo(
    () =>
      buildRequestBody({
        ddl,
        storageMode,
        coreBindingService,
        excludeAuditColumns,
        createRelationshipsFromForeignKeys,
        categoryKey,
        skipColumnSlugs,
      }),
    [
      ddl,
      storageMode,
      coreBindingService,
      excludeAuditColumns,
      createRelationshipsFromForeignKeys,
      categoryKey,
      skipColumnSlugs,
    ]
  );

  const validateCore = useCallback(() => {
    if (storageMode === 'CORE_DOMAIN' && !coreBindingService.trim()) {
      setError('Core binding service is required when storage mode is CORE_DOMAIN.');
      return false;
    }
    return true;
  }, [storageMode, coreBindingService]);

  const onPreview = useCallback(async () => {
    setError(null);
    setApplyResult(null);
    if (!ddl.trim()) {
      setError('Paste at least one CREATE TABLE statement.');
      return;
    }
    if (!validateCore()) return;
    setPreviewBusy(true);
    try {
      const res = await previewDdlImport(bodyBase);
      setPreview(res);
    } catch (e) {
      setPreview(null);
      setError(e instanceof ApiHttpError ? e.message : e instanceof Error ? e.message : 'Preview failed');
    } finally {
      setPreviewBusy(false);
    }
  }, [bodyBase, ddl, validateCore]);

  const onApply = useCallback(async () => {
    setError(null);
    if (!ddl.trim()) {
      setError('Paste at least one CREATE TABLE statement.');
      return;
    }
    if (!validateCore()) return;
    if (
      !window.confirm(
        'This will create entities, fields, and optionally relationships in your tenant. Continue?'
      )
    ) {
      return;
    }
    setApplyBusy(true);
    try {
      const res = await applyDdlImport(bodyBase);
      setApplyResult(res);
      setPreview(null);
    } catch (e) {
      setApplyResult(null);
      setError(e instanceof ApiHttpError ? e.message : e instanceof Error ? e.message : 'Apply failed');
    } finally {
      setApplyBusy(false);
    }
  }, [bodyBase, ddl, validateCore]);

  if (!canSchemaRead) {
    return (
      <div className="page-shell">
        <header className="page-header">
          <h1 className="page-title">Import from DDL</h1>
          <p className="page-sub">You need entity schema read permission to use this page.</p>
        </header>
      </div>
    );
  }

  return (
    <div className="page-shell">
      <header className="page-header">
        <div>
          <h1 className="page-title">Import from DDL</h1>
          <p className="page-sub">
            Parse <code>CREATE TABLE</code> SQL into entity definitions and fields. Preview first, then apply to persist.
          </p>
        </div>
        <Button component={Link} variant="default" to="/entities">
          Back to entities
        </Button>
      </header>

      {error && (
        <p className="text-error" role="alert" style={{ whiteSpace: 'pre-wrap' }}>
          {error}
        </p>
      )}

      <section className="ddl-import-form" style={{ marginBottom: 24 }}>
        <Textarea
          id="ddl-sql"
          label="DDL (CREATE TABLE …)"
          rows={14}
          value={ddl}
          onChange={(e) => setDdl(e.target.value)}
          placeholder={
            'CREATE TABLE example (\n  id UUID NOT NULL,\n  name VARCHAR(100) NOT NULL,\n  PRIMARY KEY (id)\n);'
          }
          spellCheck={false}
          style={{ width: '100%', fontFamily: 'ui-monospace, monospace', fontSize: 13 }}
        />

        <Stack gap="sm" mt="md" maw={560}>
          <Select
            label="Storage mode"
            value={storageMode}
            onChange={(v) => setStorageMode((v ?? 'EAV_EXTENSION') as 'EAV_EXTENSION' | 'CORE_DOMAIN')}
            data={[
              { value: 'EAV_EXTENSION', label: 'EAV_EXTENSION (extension values in entity-builder)' },
              { value: 'CORE_DOMAIN', label: 'CORE_DOMAIN (metadata only; values in domain service)' },
            ]}
          />
          {storageMode === 'CORE_DOMAIN' && (
            <TextInput
              label="Core binding service"
              value={coreBindingService}
              onChange={(e) => setCoreBindingService(e.target.value)}
              placeholder="e.g. core-service"
            />
          )}
          <Select
            id="ddl-category-key"
            label="Category (optional)"
            placeholder="None"
            value={categoryKey || null}
            onChange={(v) => setCategoryKey(v ?? '')}
            clearable
            data={categoryKeyOptions.map((k) => ({ value: k, label: formatCategoryKeyLabel(k) }))}
          />
          <Text size="sm" c="dimmed" mt={-4}>
            Same module keys as entity definitions and portal nav (<code>EntityCategoryKeys</code>).
          </Text>
          <TextInput
            label="Skip column slugs (optional, comma or newline separated)"
            value={skipColumnSlugs}
            onChange={(e) => setSkipColumnSlugs(e.target.value)}
            placeholder="legacy_col, temp_flag"
          />
          <Checkbox
            checked={excludeAuditColumns}
            onChange={(e) => setExcludeAuditColumns(e.currentTarget.checked)}
            label="Exclude audit columns (created_at, updated_at, deleted_at)"
          />
          <Checkbox
            checked={createRelationshipsFromForeignKeys}
            onChange={(e) => setCreateRelationshipsFromForeignKeys(e.currentTarget.checked)}
            label="Create entity relationships from foreign keys (when resolvable)"
          />
        </Stack>

        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginTop: 16 }}>
          <Button type="button" disabled={previewBusy} onClick={() => void onPreview()}>
            {previewBusy ? 'Previewing…' : 'Preview'}
          </Button>
          {canPlatformSchemaWrite && (
            <Button type="button" variant="default" disabled={applyBusy} onClick={() => void onApply()}>
              {applyBusy ? 'Importing…' : 'Import schema'}
            </Button>
          )}
        </div>
        {!canPlatformSchemaWrite && (
          <p className="builder-muted" style={{ marginTop: 8 }}>
            Full platform schema write ({' '}
            <code>entity_builder:schema:write</code>) is required to apply DDL import. Tenant schema users can still use
            Preview.
          </p>
        )}
      </section>

      {applyResult && (
        <section style={{ marginBottom: 24 }} aria-live="polite">
          <h2 className="page-title" style={{ fontSize: '1.1rem' }}>
            Apply complete
          </h2>
          <ul className="builder-muted">
            {applyResult.entities.map((e) => (
              <li key={e.id}>
                <strong>{e.name}</strong> (<code>{e.slug}</code>) — {e.fields.length} field(s){' '}
                <Link to={`/entities/${e.id}/layouts`}>Open layouts</Link>
              </li>
            ))}
          </ul>
          {applyResult.relationships.length > 0 && (
            <>
              <p className="builder-muted">Relationships created:</p>
              <ul>
                {applyResult.relationships.map((r) => (
                  <li key={r.id}>
                    <code>{r.slug}</code>
                  </li>
                ))}
              </ul>
            </>
          )}
          <Button component={Link} mt="sm" to="/entities">
            View all entities
          </Button>
        </section>
      )}

      {preview && (
        <section aria-live="polite">
          <h2 className="page-title" style={{ fontSize: '1.1rem' }}>
            Preview
          </h2>
          {preview.warnings.length > 0 && (
            <div className="builder-muted" style={{ marginBottom: 16 }}>
              <p>Warnings:</p>
              <ul>
                {preview.warnings.map((w, i) => (
                  <li key={i}>{w}</li>
                ))}
              </ul>
            </div>
          )}
          {preview.tables.map((t) => (
            <div key={t.proposedEntitySlug} style={{ marginBottom: 24 }}>
              <h3 style={{ fontSize: '1rem', marginBottom: 8 }}>
                {t.proposedEntityName}{' '}
                <code className="entity-card-slug">{t.proposedEntitySlug}</code>
              </h3>
              <p className="builder-muted" style={{ marginBottom: 8 }}>
                Table <code>{t.rawTableName}</code>
                {t.defaultDisplayFieldSlug ? (
                  <>
                    {' '}
                    · default display: <code>{t.defaultDisplayFieldSlug}</code>
                  </>
                ) : null}
              </p>
              <DataTable data={t.fields} columns={DDL_FIELD_COLUMNS} getRowId={(f) => f.fieldSlug} minWidth={800} />
              {t.relationships.length > 0 && (
                <Stack gap="xs" mt="md">
                  <Text size="sm" c="dimmed">
                    Relationships (if enabled on apply):
                  </Text>
                  <DataTable
                    data={t.relationships}
                    columns={DDL_REL_COLUMNS}
                    getRowId={(r) => r.relationshipSlug}
                    minWidth={560}
                  />
                </Stack>
              )}
            </div>
          ))}
        </section>
      )}
    </div>
  );
}

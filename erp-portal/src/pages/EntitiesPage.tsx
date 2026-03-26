import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Anchor, Badge, Button, Code, Group, Stack, Text, TextInput, Title } from '@mantine/core';
import { listEntities, syncSystemEntityCatalog, type EntityDto } from '../api/schemas';
import { useAuth } from '../auth/AuthProvider';
import { CreateEntityModal } from '../components/CreateEntityModal';

const SEARCH_DEBOUNCE_MS = 300;

export function EntitiesPage() {
  const navigate = useNavigate();
  const {
    canSchemaRead,
    canSchemaWrite,
    canPlatformSchemaWrite,
    canCreatePortalNavItem,
    canRecordsRead,
    tenantId,
  } = useAuth();
  const [entities, setEntities] = useState<EntityDto[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [searchInput, setSearchInput] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [listLoading, setListLoading] = useState(true);
  const [syncBusy, setSyncBusy] = useState(false);
  const [syncSuccess, setSyncSuccess] = useState<string | null>(null);
  const [syncError, setSyncError] = useState<string | null>(null);

  useEffect(() => {
    const t = window.setTimeout(() => setDebouncedSearch(searchInput.trim()), SEARCH_DEBOUNCE_MS);
    return () => window.clearTimeout(t);
  }, [searchInput]);

  const load = useCallback(async () => {
    setLoadError(null);
    setListLoading(true);
    try {
      setEntities(await listEntities({ q: debouncedSearch || undefined }));
    } catch (e) {
      setLoadError(e instanceof Error ? e.message : 'Failed to load entities');
      setEntities(null);
    } finally {
      setListLoading(false);
    }
  }, [debouncedSearch]);

  useEffect(() => {
    void load();
  }, [load]);

  async function onSyncCatalog() {
    if (!tenantId.trim() || !canPlatformSchemaWrite) return;
    setSyncSuccess(null);
    setSyncError(null);
    setSyncBusy(true);
    try {
      const { syncedManifestKeys } = await syncSystemEntityCatalog(tenantId);
      setSyncSuccess(
        syncedManifestKeys.length
          ? `Synced: ${syncedManifestKeys.join(', ')}.`
          : 'Catalog sync finished (no manifest keys returned).'
      );
      await load();
    } catch (e) {
      setSyncError(e instanceof Error ? e.message : 'Catalog sync failed');
    } finally {
      setSyncBusy(false);
    }
  }

  return (
    <Stack gap="lg" className="page-shell">
      <Group justify="space-between" align="flex-start" wrap="wrap" gap="md">
        <Stack gap={4}>
          <Title order={1} size="h2">
            Entities
          </Title>
          <Text c="dimmed" size="sm">
            Open form layouts for an entity.
          </Text>
        </Stack>
        <Group gap="xs" wrap="wrap">
          {canSchemaWrite && (
            <Button variant="default" size="sm" onClick={() => setCreateOpen(true)}>
              Create entity
            </Button>
          )}
          {canSchemaWrite && tenantId.trim() && (
            <Button
              variant="default"
              size="sm"
              disabled={syncBusy}
              onClick={() => void onSyncCatalog()}
              title="Import bundled system-entity-catalog manifests for this tenant"
            >
              {syncBusy ? 'Syncing catalog…' : 'Sync system catalog'}
            </Button>
          )}
          {canSchemaRead && (
            <Button component={Link} to="/entities/ddl-import" variant="default" size="sm">
              Import from DDL
            </Button>
          )}
          {canCreatePortalNavItem && (
            <Button component={Link} to="/ui/create" variant="default" size="sm">
              Create UI
            </Button>
          )}
        </Group>
      </Group>
      {syncSuccess && (
        <Text size="sm" c="dimmed" role="status">
          {syncSuccess}
        </Text>
      )}
      {syncError && (
        <Text role="alert" c="red" size="sm">
          {syncError}
        </Text>
      )}
      {loadError && (
        <Text role="alert" c="red" size="sm">
          {loadError}
        </Text>
      )}
      <Group align="flex-end" wrap="wrap" gap="md" role="search">
        <TextInput
          id="entities-search"
          label="Search"
          placeholder="Name, slug, or id…"
          value={searchInput}
          onChange={(e) => setSearchInput(e.target.value)}
          autoComplete="off"
          style={{ flex: '1 1 240px', maxWidth: 400 }}
          size="sm"
        />
        {listLoading && (
          <Text size="sm" c="dimmed" mb={4}>
            Searching…
          </Text>
        )}
        {canSchemaWrite && (
          <Button variant="default" size="xs" onClick={() => setCreateOpen(true)}>
            Create entity
          </Button>
        )}
      </Group>
      <ul className="entity-list">
        {entities?.map((e) => (
          <li key={e.id} className="entity-list-row">
            <div
              className="entity-card entity-card-static"
              style={{ flexDirection: 'column', alignItems: 'stretch', gap: 8 }}
            >
              <Group gap="xs" wrap="wrap" align="center">
                <Anchor component={Link} to={`/entities/${e.id}/records`} fw={600} className="entity-card-name focusable">
                  {e.name}
                </Anchor>
                <Code className="entity-card-slug">{e.slug}</Code>
                {e.definitionScope === 'STANDARD_OBJECT' && (
                  <Badge size="sm" variant="outline" title="Platform catalog entity — definition/fields require full schema write">
                    Catalog
                  </Badge>
                )}
              </Group>
              {e.description != null && e.description.trim() !== '' && (
                <Text size="sm" c="dimmed" lineClamp={4} style={{ lineHeight: 1.45 }}>
                  {e.description.trim()}
                </Text>
              )}
            </div>
            <div className="entity-card-actions">
              <Button component={Link} to={`/entities/${e.id}/layouts`} size="xs">
                Layouts
              </Button>
              {canRecordsRead && (
                <Button component={Link} to={`/entities/${e.id}/audit`} variant="default" size="xs">
                  Activity
                </Button>
              )}
            </div>
          </li>
        ))}
      </ul>
      {entities && entities.length === 0 && (
        <Stack gap="sm" mt="sm">
          {debouncedSearch ? (
            <Text c="dimmed" size="sm">
              No entities match &quot;{debouncedSearch}&quot;.
            </Text>
          ) : (
            <>
              <Text c="dimmed" size="sm">
                No entities yet.
              </Text>
              {canSchemaWrite && (
                <Button size="sm" onClick={() => setCreateOpen(true)}>
                  Create your first entity
                </Button>
              )}
            </>
          )}
        </Stack>
      )}
      <Button variant="default" size="xs" mt="md" onClick={() => void load()}>
        Reload
      </Button>
      {createOpen && (
        <CreateEntityModal
          onClose={() => setCreateOpen(false)}
          onCreated={(e) => navigate(`/entities/${e.id}/layouts`)}
        />
      )}
    </Stack>
  );
}

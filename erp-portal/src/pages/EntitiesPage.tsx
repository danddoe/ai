import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { listEntities, syncSystemEntityCatalog, type EntityDto } from '../api/schemas';
import { useAuth } from '../auth/AuthProvider';
import { CreateEntityModal } from '../components/CreateEntityModal';

const SEARCH_DEBOUNCE_MS = 300;

export function EntitiesPage() {
  const navigate = useNavigate();
  const { canSchemaWrite, canCreatePortalNavItem, canRecordsRead, tenantId } = useAuth();
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
    if (!tenantId.trim() || !canSchemaWrite) return;
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
    <div className="page-shell">
      <header className="page-header">
        <div>
          <h1 className="page-title">Entities</h1>
          <p className="page-sub">Open form layouts for an entity.</p>
        </div>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, alignItems: 'center' }}>
          {canSchemaWrite && tenantId.trim() && (
            <button
              type="button"
              className="btn btn-secondary"
              disabled={syncBusy}
              onClick={() => void onSyncCatalog()}
              title="Import bundled system-entity-catalog manifests for this tenant"
            >
              {syncBusy ? 'Syncing catalog…' : 'Sync system catalog'}
            </button>
          )}
          {canCreatePortalNavItem && (
            <Link className="btn btn-secondary" to="/ui/create">
              Create UI
            </Link>
          )}
        </div>
      </header>
      {syncSuccess && (
        <p className="builder-muted" role="status">
          {syncSuccess}
        </p>
      )}
      {syncError && (
        <p className="text-error" role="alert">
          {syncError}
        </p>
      )}
      {loadError && (
        <p role="alert" className="text-error">
          {loadError}
        </p>
      )}
      <div className="entity-list-toolbar" role="search">
        <label className="field-label row entity-list-search-label" htmlFor="entities-search">
          Search
          <input
            id="entities-search"
            type="search"
            className="input entity-list-search-input"
            placeholder="Name, slug, or id…"
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            autoComplete="off"
            spellCheck={false}
          />
        </label>
        {listLoading && <span className="builder-muted entity-list-search-status">Searching…</span>}
      </div>
      <ul className="entity-list">
        {entities?.map((e) => (
          <li key={e.id} className="entity-list-row">
            <div className="entity-card entity-card-static">
              <Link className="entity-card-name focusable" to={`/entities/${e.id}/records`}>
                {e.name}
              </Link>
              <code className="entity-card-slug">{e.slug}</code>
            </div>
            <div className="entity-card-actions">
              <Link className="btn btn-primary btn-sm" to={`/entities/${e.id}/layouts`}>
                Layouts
              </Link>
              {canRecordsRead && (
                <Link className="btn btn-secondary btn-sm" to={`/entities/${e.id}/audit`}>
                  Activity
                </Link>
              )}
            </div>
          </li>
        ))}
      </ul>
      {entities && entities.length === 0 && (
        <div className="builder-muted" style={{ marginTop: 8 }}>
          {debouncedSearch ? (
            <p>No entities match &quot;{debouncedSearch}&quot;.</p>
          ) : (
            <>
              <p>No entities yet.</p>
              {canSchemaWrite && (
                <button type="button" className="btn btn-primary btn-sm" onClick={() => setCreateOpen(true)}>
                  Create your first entity
                </button>
              )}
            </>
          )}
        </div>
      )}
      <button type="button" className="btn btn-secondary btn-sm" style={{ marginTop: 16 }} onClick={() => void load()}>
        Reload
      </button>
      {createOpen && (
        <CreateEntityModal
          onClose={() => setCreateOpen(false)}
          onCreated={(e) => navigate(`/entities/${e.id}/layouts`)}
        />
      )}
    </div>
  );
}

import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { listEntities, type EntityDto } from '../api/schemas';
import { useAuth } from '../auth/AuthProvider';

/**
 * Entry point for entity-wide audit timelines; linked from portal nav and global search.
 */
export function AuditHubPage() {
  const { tenantId, canRecordsRead } = useAuth();
  const [entities, setEntities] = useState<EntityDto[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    if (!tenantId || !canRecordsRead) {
      setLoading(false);
      return;
    }
    setError(null);
    setLoading(true);
    try {
      setEntities(await listEntities());
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load entities');
      setEntities(null);
    } finally {
      setLoading(false);
    }
  }, [tenantId, canRecordsRead]);

  useEffect(() => {
    void load();
  }, [load]);

  if (!tenantId) {
    return (
      <div className="page-shell">
        <p role="alert" className="text-error">
          Missing <code>tenant_id</code> in access token.
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

  return (
    <div className="page-shell">
      <nav className="breadcrumb">
        <Link to="/home">Home</Link>
        <span aria-hidden> / </span>
        <span>Activity & audit</span>
      </nav>
      <header className="page-header">
        <div>
          <h1 className="page-title">Activity & audit</h1>
          <p className="page-sub">Open a change history for all records of an entity.</p>
        </div>
      </header>
      {error && (
        <p role="alert" className="text-error">
          {error}
        </p>
      )}
      {loading && <p className="builder-muted">Loading entities…</p>}
      {!loading && entities && entities.length === 0 && (
        <p className="builder-muted">No entities yet. Create one under Entities.</p>
      )}
      {!loading && entities && entities.length > 0 && (
        <ul className="entity-list">
          {entities.map((e) => (
            <li key={e.id} className="entity-list-row">
              <div className="entity-card entity-card-static">
                <span className="entity-card-name">{e.name}</span>
                <code className="entity-card-slug">{e.slug}</code>
              </div>
              <div className="entity-card-actions">
                <Link className="btn btn-primary btn-sm" to={`/entities/${e.id}/audit`}>
                  View activity
                </Link>
                <Link className="btn btn-secondary btn-sm" to={`/entities/${e.id}/records`}>
                  Records
                </Link>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  auditEventActorDisplay,
  getEntity,
  listEntityAuditEvents,
  type AuditEventDto,
  type EntityDto,
} from '../api/schemas';
import { useAuth } from '../auth/AuthProvider';
import { AuditPayloadModal } from '../components/AuditPayloadModal';

export function EntityAuditPage() {
  const { entityId = '' } = useParams();
  const { tenantId, canRecordsRead } = useAuth();
  const [entity, setEntity] = useState<EntityDto | null>(null);
  const [items, setItems] = useState<AuditEventDto[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const pageSize = 50;
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [payloadOpen, setPayloadOpen] = useState<AuditEventDto | null>(null);

  const loadEntity = useCallback(async () => {
    if (!entityId) return;
    try {
      setEntity(await getEntity(entityId));
    } catch {
      setEntity(null);
    }
  }, [entityId]);

  const loadAudit = useCallback(async () => {
    if (!tenantId || !entityId || !canRecordsRead) return;
    setError(null);
    setLoading(true);
    try {
      const res = await listEntityAuditEvents(tenantId, entityId, { page, pageSize });
      setItems(res.items);
      setTotal(res.total);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load audit events');
      setItems([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [tenantId, entityId, canRecordsRead, page]);

  useEffect(() => {
    void loadEntity();
  }, [loadEntity]);

  useEffect(() => {
    void loadAudit();
  }, [loadAudit]);

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

  const totalPages = Math.max(1, Math.ceil(total / pageSize));

  return (
    <div className="page-shell page-shell-wide">
      <nav className="breadcrumb">
        <Link to="/entities">Entities</Link>
        <span aria-hidden> / </span>
        <Link to={`/entities/${entityId}/records`}>{entity?.name ?? '…'}</Link>
        <span aria-hidden> / </span>
        <span>Activity</span>
      </nav>
      <header className="page-header">
        <div>
          <h1 className="page-title">Entity activity</h1>
          <p className="page-sub">
            {entity ? `${entity.name} — audit events for all records.` : 'Loading…'}
          </p>
        </div>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          <Link className="btn btn-secondary" to="/audit">
            All entities
          </Link>
          <Link className="btn btn-secondary" to={`/entities/${entityId}/records`}>
            Records
          </Link>
        </div>
      </header>
      {error && (
        <p role="alert" className="text-error">
          {error}
        </p>
      )}
      {loading && <p className="builder-muted">Loading…</p>}
      {!loading && !error && (
        <>
          <p className="builder-muted" style={{ marginBottom: 12 }}>
            {total === 0
              ? 'No audit events for this entity.'
              : `Page ${page} of ${totalPages} · ${total} event(s) total (newest first).`}
          </p>
          {items.length > 0 && (
            <div className="records-table-wrap">
              <table className="records-table">
                <thead>
                  <tr>
                    <th>When</th>
                    <th>Action</th>
                    <th>Operation</th>
                    <th>Record</th>
                    <th>Actor</th>
                    <th>Payload</th>
                  </tr>
                </thead>
                <tbody>
                  {items.map((e) => (
                    <tr key={e.id}>
                      <td>{new Date(e.createdAt).toLocaleString()}</td>
                      <td>
                        <code>{e.action}</code>
                      </td>
                      <td>{e.operation ?? '—'}</td>
                      <td>
                        {e.resourceId ? (
                          <Link to={`/entities/${entityId}/records/${e.resourceId}`}>
                            <code>{e.resourceId.slice(0, 8)}…</code>
                          </Link>
                        ) : (
                          '—'
                        )}
                      </td>
                      <td>
                        {(() => {
                          const display = auditEventActorDisplay(e);
                          const idOnly = display === (e.actorId ?? '—');
                          return idOnly ? (
                            <code>{display}</code>
                          ) : (
                            <span title={e.actorId ? `User id: ${e.actorId}` : undefined}>{display}</span>
                          );
                        })()}
                      </td>
                      <td style={{ maxWidth: 140 }}>
                        <button
                          type="button"
                          className="btn btn-secondary btn-sm"
                          onClick={() => setPayloadOpen(e)}
                        >
                          View payload
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          {totalPages > 1 && (
            <div style={{ display: 'flex', gap: 8, marginTop: 16, flexWrap: 'wrap' }}>
              <button
                type="button"
                className="btn btn-secondary btn-sm"
                disabled={page <= 1}
                onClick={() => setPage((p) => Math.max(1, p - 1))}
              >
                Previous
              </button>
              <button
                type="button"
                className="btn btn-secondary btn-sm"
                disabled={page >= totalPages}
                onClick={() => setPage((p) => p + 1)}
              >
                Next
              </button>
            </div>
          )}
        </>
      )}
      {payloadOpen && (
        <AuditPayloadModal
          payload={payloadOpen.payload}
          title={`Payload · ${payloadOpen.action} · ${new Date(payloadOpen.createdAt).toLocaleString()}`}
          onClose={() => setPayloadOpen(null)}
        />
      )}
    </div>
  );
}

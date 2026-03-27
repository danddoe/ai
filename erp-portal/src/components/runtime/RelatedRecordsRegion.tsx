import { Anchor, Button, Text } from '@mantine/core';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  deleteRecordLink,
  getRecord,
  listRecordLinks,
  type EntityDto,
  type EntityRelationshipDto,
  type RecordDto,
} from '../../api/schemas';

const FETCH_CONCURRENCY = 5;

function chunk<T>(arr: T[], size: number): T[][] {
  const out: T[][] = [];
  for (let i = 0; i < arr.length; i += size) {
    out.push(arr.slice(i, i + size));
  }
  return out;
}

function displayCell(rec: RecordDto, childEntity: EntityDto | undefined): string {
  const slug = childEntity?.defaultDisplayFieldSlug?.trim();
  if (slug) {
    const v = rec.values[slug];
    if (v != null && v !== '') return String(v);
  }
  for (const k of Object.keys(rec.values)) {
    const v = rec.values[k];
    if (v != null && v !== '') return String(v);
  }
  return '—';
}

function shortId(id: string): string {
  return id.length > 10 ? `${id.slice(0, 8)}…` : id;
}

export type LinkAfterCreateState = {
  parentEntityId: string;
  parentRecordId: string;
  relationshipSlug: string;
  /** FK field on the child (`to`) entity pointing at the parent; form defaults this to {@link parentRecordId}. */
  toFieldSlug: string | null;
  /** Parent entity display name for breadcrumbs (optional; resolved from schema if omitted). */
  parentEntityName?: string | null;
};

type Props = {
  tenantId: string;
  hostEntityId: string;
  parentRecordId: string | null;
  relationshipId: string;
  relationships: EntityRelationshipDto[];
  allEntities: EntityDto[];
  canWrite: boolean;
};

export function RelatedRecordsRegion({
  tenantId,
  hostEntityId,
  parentRecordId,
  relationshipId,
  relationships,
  allEntities,
  canWrite,
}: Props) {
  const navigate = useNavigate();
  const rel = useMemo(() => relationships.find((r) => r.id === relationshipId), [relationships, relationshipId]);
  const childEntity = useMemo(
    () => (rel ? allEntities.find((e) => e.id === rel.toEntityId) : undefined),
    [allEntities, rel]
  );

  const [rows, setRows] = useState<{ toRecordId: string; record: RecordDto }[]>([]);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [refreshKey, setRefreshKey] = useState(0);
  const [actionError, setActionError] = useState<string | null>(null);

  const reload = useCallback(() => {
    setRefreshKey((k) => k + 1);
  }, []);

  useEffect(() => {
    if (!rel || rel.fromEntityId !== hostEntityId) {
      setRows([]);
      setLoadError(null);
      return;
    }
    if (!parentRecordId) {
      setRows([]);
      setLoadError(null);
      return;
    }
    if (!childEntity) {
      setRows([]);
      setLoadError(null);
      return;
    }

    let cancelled = false;
    (async () => {
      setLoading(true);
      setLoadError(null);
      try {
        const links = await listRecordLinks(tenantId, parentRecordId);
        const toIds = links.filter((l) => l.relationshipSlug === rel.slug).map((l) => l.toRecordId);
        const collected: { toRecordId: string; record: RecordDto }[] = [];
        for (const batch of chunk(toIds, FETCH_CONCURRENCY)) {
          const batchRows = await Promise.all(
            batch.map(async (toRecordId) => {
              const record = await getRecord(tenantId, childEntity.id, toRecordId);
              return { toRecordId, record };
            })
          );
          collected.push(...batchRows);
        }
        if (!cancelled) setRows(collected);
      } catch (e) {
        if (!cancelled) {
          setRows([]);
          setLoadError(e instanceof Error ? e.message : 'Failed to load related records');
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [tenantId, parentRecordId, rel, hostEntityId, childEntity, refreshKey]);

  if (!rel) {
    return (
      <Text size="sm" c="red">
        Unknown relationship (id may have been deleted).
      </Text>
    );
  }

  if (rel.fromEntityId !== hostEntityId) {
    return (
      <Text size="sm" c="red">
        This region is bound to a relationship whose parent is not this form&apos;s entity. Fix the layout binding in the form
        designer.
      </Text>
    );
  }

  if (!childEntity) {
    return (
      <Text size="sm" c="dimmed">
        Child entity for this relationship is not loaded.
      </Text>
    );
  }

  if (!parentRecordId) {
    return (
      <Text size="sm" c="dimmed">
        Save this record first to view and manage related {childEntity.name} rows.
      </Text>
    );
  }

  const card = (rel.cardinality || '').toLowerCase();
  const isOneToOne = card === 'one-to-one';
  const showAdd = canWrite && (!isOneToOne || rows.length === 0);

  async function onUnlink(toRecordId: string) {
    if (!canWrite || !parentRecordId) return;
    if (!window.confirm('Remove the link to this record? The related record is not deleted.')) return;
    setActionError(null);
    try {
      await deleteRecordLink(tenantId, parentRecordId, { relationshipSlug: rel.slug, toRecordId });
      reload();
    } catch (e) {
      setActionError(e instanceof Error ? e.message : 'Unlink failed');
    }
  }

  function onAdd() {
    if (!canWrite || !parentRecordId) return;
    navigate(`/entities/${childEntity.id}/records/new`, {
      state: {
        linkAfterCreate: {
          parentEntityId: hostEntityId,
          parentRecordId,
          relationshipSlug: rel.slug,
          toFieldSlug: rel.toFieldSlug,
          parentEntityName: allEntities.find((e) => e.id === hostEntityId)?.name ?? null,
        } satisfies LinkAfterCreateState,
      },
    });
  }

  return (
    <div className="runtime-related-records">
      {loadError ? (
        <Text size="sm" c="red" mb="sm">
          {loadError}
        </Text>
      ) : null}
      {actionError ? (
        <Text size="sm" c="red" mb="sm">
          {actionError}
        </Text>
      ) : null}
      {loading ? (
        <Text size="sm" c="dimmed" mb="sm">
          Loading…
        </Text>
      ) : null}

      <div style={{ display: 'flex', gap: 8, marginBottom: 12, flexWrap: 'wrap', alignItems: 'center' }}>
        {showAdd ? (
          <Button type="button" size="xs" variant="light" onClick={onAdd}>
            Add {childEntity.name}
          </Button>
        ) : null}
      </div>

      {rows.length === 0 && !loading ? (
        <Text size="sm" c="dimmed">
          No linked {childEntity.name} yet.
        </Text>
      ) : (
        <div style={{ overflowX: 'auto' }}>
          <table className="runtime-related-records-table" style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.875rem' }}>
            <thead>
              <tr style={{ textAlign: 'left', borderBottom: '1px solid var(--mantine-color-gray-3)' }}>
                <th style={{ padding: '8px 10px 8px 0' }}>Display</th>
                <th style={{ padding: '8px 10px' }}>Updated</th>
                <th style={{ padding: '8px 0 8px 10px', width: 1 }} />
              </tr>
            </thead>
            <tbody>
              {rows.map(({ toRecordId, record }) => (
                <tr key={toRecordId} style={{ borderBottom: '1px solid var(--mantine-color-gray-2)' }}>
                  <td style={{ padding: '8px 10px 8px 0' }}>
                    <Anchor component={Link} to={`/entities/${childEntity.id}/records/${toRecordId}`} size="sm">
                      {displayCell(record, childEntity)}
                    </Anchor>
                    <Text size="xs" c="dimmed" span style={{ marginLeft: 8 }} title={toRecordId}>
                      {shortId(toRecordId)}
                    </Text>
                  </td>
                  <td style={{ padding: '8px 10px', whiteSpace: 'nowrap' }}>
                    {new Date(record.updatedAt).toLocaleString()}
                  </td>
                  <td style={{ padding: '8px 0 8px 10px', whiteSpace: 'nowrap', textAlign: 'right' }}>
                    {canWrite ? (
                      <Button type="button" size="xs" variant="subtle" color="red" onClick={() => void onUnlink(toRecordId)}>
                        Unlink
                      </Button>
                    ) : null}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

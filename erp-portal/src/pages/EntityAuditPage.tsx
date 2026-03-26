import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Anchor, Button, Group, Stack, Text, Title } from '@mantine/core';
import { getEntity, listEntityAuditEvents, type AuditEventDto, type EntityDto } from '../api/schemas';
import { useAuth } from '../auth/AuthProvider';
import { AuditEventsTable } from '../components/audit/AuditEventsTable';
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

  const onViewPayload = useCallback((e: AuditEventDto) => setPayloadOpen(e), []);

  return (
    <Stack gap="lg" className="page-shell page-shell-wide" maw={1200}>
      <Group gap="xs" wrap="wrap">
        <Anchor component={Link} to="/entities" size="sm">
          Entities
        </Anchor>
        <Text span c="dimmed" aria-hidden>
          /
        </Text>
        <Anchor component={Link} to={`/entities/${entityId}/records`} size="sm">
          {entity?.name ?? '…'}
        </Anchor>
        <Text span c="dimmed" aria-hidden>
          /
        </Text>
        <Text span size="sm">
          Activity
        </Text>
      </Group>

      <Group justify="space-between" align="flex-start" wrap="wrap" gap="md">
        <Stack gap={4}>
          <Title order={1} size="h2">
            Entity activity
          </Title>
          <Text c="dimmed" size="sm">
            {entity ? `${entity.name} — audit events for all records.` : 'Loading…'}
          </Text>
        </Stack>
        <Group gap="xs">
          <Button component={Link} to="/audit" variant="default" size="sm">
            All entities
          </Button>
          <Button component={Link} to={`/entities/${entityId}/records`} variant="default" size="sm">
            Records
          </Button>
        </Group>
      </Group>

      {error && (
        <Text role="alert" c="red" size="sm">
          {error}
        </Text>
      )}
      {loading && (
        <Text size="sm" c="dimmed">
          Loading…
        </Text>
      )}
      {!loading && !error && (
        <>
          <Text size="sm" c="dimmed">
            {total === 0
              ? 'No audit events for this entity.'
              : `Page ${page} of ${totalPages} · ${total} event(s) total (newest first).`}
          </Text>
          {items.length > 0 && (
            <AuditEventsTable
              items={items}
              entityId={entityId}
              includeRecordColumn
              onViewPayload={onViewPayload}
            />
          )}
          {totalPages > 1 && (
            <Group gap="xs" mt="sm">
              <Button
                type="button"
                variant="default"
                size="xs"
                disabled={page <= 1}
                onClick={() => setPage((p) => Math.max(1, p - 1))}
              >
                Previous
              </Button>
              <Button
                type="button"
                variant="default"
                size="xs"
                disabled={page >= totalPages}
                onClick={() => setPage((p) => p + 1)}
              >
                Next
              </Button>
            </Group>
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
    </Stack>
  );
}

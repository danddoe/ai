import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Anchor, Button, Code, Group, Stack, Text, Title } from '@mantine/core';
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
      <Stack className="page-shell">
        <Text role="alert" c="red" size="sm">
          Missing <code>tenant_id</code> in access token.
        </Text>
      </Stack>
    );
  }

  if (!canRecordsRead) {
    return (
      <Stack className="page-shell">
        <Text role="alert" c="red" size="sm">
          Missing <code>entity_builder:records:read</code> permission.
        </Text>
      </Stack>
    );
  }

  return (
    <Stack gap="lg" className="page-shell">
      <Group gap="xs" wrap="wrap">
        <Anchor component={Link} to="/home" size="sm">
          Home
        </Anchor>
        <Text span c="dimmed" aria-hidden>
          /
        </Text>
        <Text span size="sm">
          Activity & audit
        </Text>
      </Group>
      <div>
        <Title order={1} size="h2" mb="xs">
          Activity & audit
        </Title>
        <Text c="dimmed" size="sm">
          Open a change history for all records of an entity.
        </Text>
      </div>
      {error && (
        <Text role="alert" c="red" size="sm">
          {error}
        </Text>
      )}
      {loading && (
        <Text size="sm" c="dimmed">
          Loading entities…
        </Text>
      )}
      {!loading && entities && entities.length === 0 && (
        <Text size="sm" c="dimmed">
          No entities yet. Create one under Entities.
        </Text>
      )}
      {!loading && entities && entities.length > 0 && (
        <ul className="entity-list">
          {entities.map((e) => (
            <li key={e.id} className="entity-list-row">
              <div className="entity-card entity-card-static">
                <span className="entity-card-name">{e.name}</span>
                <Code className="entity-card-slug">{e.slug}</Code>
              </div>
              <div className="entity-card-actions">
                <Button component={Link} to={`/entities/${e.id}/audit`} size="xs">
                  View activity
                </Button>
                <Button component={Link} to={`/entities/${e.id}/records`} variant="default" size="xs">
                  Records
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </Stack>
  );
}

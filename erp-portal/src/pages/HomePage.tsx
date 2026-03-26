import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { Anchor, Button, Paper, Stack, Text, Title } from '@mantine/core';
import { usePortalNavigation } from '../hooks/usePortalNavigation';
import { useAuth } from '../auth/AuthProvider';
import { SAMPLE_SUPER_AI_TENANT, seedSampleSuperAiTenant } from '../dev/sampleTenantSeed';

function formatCategory(key: string | null, generalLabel: string): string {
  if (!key) return generalLabel;
  return key.replace(/_/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase());
}

export function HomePage() {
  const { t } = useTranslation();
  const { items, state } = usePortalNavigation();
  const { canCreatePortalNavItem, canManageGlobalNavigation, canSchemaRead, canRunSampleTenantSeed } = useAuth();
  const [seedBusy, setSeedBusy] = useState(false);
  const [seedMsg, setSeedMsg] = useState<string | null>(null);
  const [seedErr, setSeedErr] = useState<string | null>(null);

  const isDev = import.meta.env.DEV;

  return (
    <Stack gap="lg" className="page-shell page-shell-wide" maw={960}>
      <div>
        <Title order={1} size="h2" mb="xs">
          {t('home.title')}
        </Title>
        <Text c="dimmed" size="sm">
          {t('home.welcome')}
        </Text>
      </div>

      {isDev && canRunSampleTenantSeed && (
        <Paper
          withBorder
          p="md"
          radius="md"
          maw={720}
          aria-label="Development sample tenant"
          className="home-dev-seed"
        >
          <Title order={2} size="h4" mb="sm">
            Dev: sample tenant (client-side seed)
          </Title>
          <Text size="sm" c="dimmed" mb="md">
            Creates <strong>{SAMPLE_SUPER_AI_TENANT.name}</strong> (slug <code>{SAMPLE_SUPER_AI_TENANT.slug}</code>) and{' '}
            <strong>{SAMPLE_SUPER_AI_TENANT.adminEmail}</strong> with tenant schema permissions only (cannot edit platform catalog
            entities; can create entities, extensions, layouts). Requires your current user to have IAM tenant/security/superadmin
            access. IAM must include Flyway <code>V22</code> (tenant schema permission).
          </Text>
          <Button
            variant="default"
            size="sm"
            disabled={seedBusy}
            onClick={() => {
              setSeedMsg(null);
              setSeedErr(null);
              const pwd = window.prompt(
                `Password for ${SAMPLE_SUPER_AI_TENANT.adminEmail} (min 8 characters)?`
              );
              if (pwd == null) return;
              setSeedBusy(true);
              void seedSampleSuperAiTenant(pwd)
                .then((r) => {
                  setSeedMsg(r.message);
                })
                .catch((e: unknown) => {
                  setSeedErr(e instanceof Error ? e.message : 'Seed failed');
                })
                .finally(() => setSeedBusy(false));
            }}
          >
            {seedBusy ? 'Creating…' : 'Create sample tenant + admin'}
          </Button>
          {seedMsg && (
            <Text role="status" size="sm" c="dimmed" mt="sm">
              {seedMsg}
            </Text>
          )}
          {seedErr && (
            <Text role="alert" c="red" size="sm" mt="sm">
              {seedErr}
            </Text>
          )}
        </Paper>
      )}

      {state.status === 'error' && (
        <Text role="alert" c="red" size="sm">
          {t('home.navErrorBefore')}{' '}
          <Anchor component={Link} to="/entities">
            {t('home.navErrorLink')}
          </Anchor>
          {t('home.navErrorAfter')}
        </Text>
      )}

      <section className="home-modules" aria-label="Modules">
        {items.map((root) => (
          <div key={root.id} className="home-module-card">
            <h2 className="home-module-title">
              {root.label}
              {root.categoryKey && (
                <span className="home-module-cat">{formatCategory(root.categoryKey, t('home.general'))}</span>
              )}
            </h2>
            {root.description && <p className="home-module-desc">{root.description}</p>}
            <ul className="home-module-links">
              {root.children?.map((ch) => (
                <li key={ch.id}>
                  {ch.routePath && ch.type === 'internal' ? (
                    <Link to={ch.routePath}>{ch.label}</Link>
                  ) : ch.routePath && ch.type === 'external' ? (
                    <a href={ch.routePath} target="_blank" rel="noreferrer">
                      {ch.label}
                    </a>
                  ) : (
                    <span className="builder-muted">{ch.label}</span>
                  )}
                </li>
              ))}
            </ul>
          </div>
        ))}
      </section>

      <Text size="sm" c="dimmed" mt="lg">
        {canCreatePortalNavItem && (
          <>
            <Anchor component={Link} to="/ui/create">
              Create UI (template → entity → nav) →
            </Anchor>
            {' · '}
          </>
        )}
        {(canCreatePortalNavItem || canManageGlobalNavigation) && (
          <>
            <Anchor component={Link} to="/settings/navigation-items">
              Navigation items →
            </Anchor>
            {' · '}
          </>
        )}
        <Anchor component={Link} to="/entities">
          Browse all entities →
        </Anchor>
        {canSchemaRead && (
          <>
            {' · '}
            <Anchor component={Link} to="/entities/ddl-import">
              Import from DDL →
            </Anchor>
          </>
        )}
        {' · '}
        <Anchor component={Link} to="/loans-module/hybrid-demo">
          Hybrid loan (core + EAV) demo →
        </Anchor>
      </Text>
    </Stack>
  );
}

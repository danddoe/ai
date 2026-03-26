import { useEffect, useState } from 'react';
import { Link, Outlet, useLocation } from 'react-router-dom';
import { Anchor, AppShell, Badge, Box, Burger, Button, Group } from '@mantine/core';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../auth/AuthProvider';
import { GlobalSearch } from '../components/GlobalSearch';
import { LanguageSwitcher } from '../components/LanguageSwitcher';
import { NavSidebar } from '../components/NavSidebar';
import { usePortalNavigation } from '../hooks/usePortalNavigation';

const COLLAPSE_KEY = 'erp_nav_collapsed';

/** Full-width builder chrome: hide module sidebar, show builder badge. */
function layoutBuilderMode(pathname: string): 'form' | 'list' | null {
  if (/\/entities\/[^/]+\/layouts\/[^/]+$/.test(pathname)) return 'form';
  if (/\/entities\/[^/]+\/list-views\//.test(pathname)) return 'list';
  return null;
}

export function AppLayout() {
  const { t } = useTranslation();
  const location = useLocation();
  const { logout } = useAuth();
  const { items, state: navState } = usePortalNavigation();
  const builderMode = layoutBuilderMode(location.pathname);

  const [collapsed, setCollapsed] = useState(() => {
    try {
      return sessionStorage.getItem(COLLAPSE_KEY) === '1';
    } catch {
      return false;
    }
  });

  useEffect(() => {
    try {
      sessionStorage.setItem(COLLAPSE_KEY, collapsed ? '1' : '0');
    } catch {
      /* ignore */
    }
  }, [collapsed]);

  const sidebarVisible = builderMode === null;

  return (
    <AppShell
      header={{ height: 56 }}
      padding="md"
      {...(sidebarVisible
        ? { navbar: { width: collapsed ? 56 : 280, breakpoint: 0 } }
        : {})}
    >
      <AppShell.Header>
        <Group h="100%" px="sm" justify="space-between" gap="md" wrap="nowrap">
          <Group gap="sm" wrap="nowrap">
            {sidebarVisible && (
              <Burger
                opened={!collapsed}
                onClick={() => setCollapsed((c) => !c)}
                size="sm"
                aria-label={collapsed ? t('header.expandSidebar') : t('header.collapseSidebar')}
              />
            )}
            <Anchor component={Link} to="/home" fw={700} c="var(--mantine-color-text)" underline="never">
              {t('appTitle')}
            </Anchor>
            {builderMode === 'form' && (
              <Badge size="sm" variant="light" title={t('header.formBuilderTitle')}>
                {t('header.formBuilderBadge')}
              </Badge>
            )}
            {builderMode === 'list' && (
              <Badge size="sm" variant="light" title={t('header.listDesignerTitle')}>
                {t('header.listDesignerBadge')}
              </Badge>
            )}
          </Group>
          <Box style={{ flex: 1, minWidth: 0, display: 'flex', justifyContent: 'center' }}>
            <Box maw={560} w="100%">
              <GlobalSearch />
            </Box>
          </Box>
          <Group gap="xs" wrap="nowrap">
            <LanguageSwitcher size="xs" />
            <Button variant="subtle" size="sm" onClick={() => void logout()}>
              {t('header.logOut')}
            </Button>
          </Group>
        </Group>
      </AppShell.Header>

      {sidebarVisible && (
        <AppShell.Navbar p={0} withBorder>
          <NavSidebar
            items={items}
            collapsed={collapsed}
            onExpand={() => setCollapsed(false)}
          />
        </AppShell.Navbar>
      )}

      <AppShell.Main>
        {navState.status === 'error' && builderMode === null && (
          <Box component="p" className="app-nav-warn" role="status" mb="sm">
            {t('nav.loadError', { message: navState.message })}
          </Box>
        )}
        <Outlet />
      </AppShell.Main>
    </AppShell>
  );
}

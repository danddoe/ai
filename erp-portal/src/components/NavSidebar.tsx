import { useTranslation } from 'react-i18next';
import { NavLink } from 'react-router-dom';
import { ActionIcon, Badge, Box, ScrollArea } from '@mantine/core';
import type { NavigationItemDto } from '../api/schemas';
import { IconChevronsRight } from '../ui/icons/IconChevronsRight';
import { PortalNavIcon } from '../ui/portalNavIcons';

function NavBranch({
  nodes,
  depth,
  t,
}: {
  nodes: NavigationItemDto[];
  depth: number;
  t: (key: string, opt?: Record<string, string>) => string;
}) {
  return (
    <ul className={`nav-tree${depth > 0 ? ' nav-tree-nested' : ''}`}>
      {nodes
        .slice()
        .sort((a, b) => a.sortOrder - b.sortOrder)
        .map((n) => (
          <li key={n.id}>
            {n.routePath && (n.type === 'internal' || n.type === 'external') ? (
              n.type === 'external' ? (
                <a href={n.routePath} className="nav-tree-link" target="_blank" rel="noreferrer">
                  <PortalNavIcon name={n.icon} className="nav-tree-icon" />
                  {n.label}
                  {(n.designStatus ?? 'PUBLISHED') === 'WIP' && (
                    <Badge size="xs" variant="outline" color="orange" ml={6} title={t('nav.wipTitle')}>
                      {t('nav.wipBadge')}
                    </Badge>
                  )}
                </a>
              ) : (
                <NavLink
                  to={n.routePath}
                  className={({ isActive }) => `nav-tree-link${isActive ? ' nav-tree-link-active' : ''}`}
                  end={
                    (() => {
                      const p = n.routePath?.split('?')[0] ?? '';
                      return p === '/entities' || p === '/home';
                    })()
                  }
                >
                  <PortalNavIcon name={n.icon} className="nav-tree-icon" />
                  {n.label}
                  {(n.designStatus ?? 'PUBLISHED') === 'WIP' && (
                    <Badge size="xs" variant="outline" color="orange" ml={6} title={t('nav.wipTitle')}>
                      {t('nav.wipBadge')}
                    </Badge>
                  )}
                </NavLink>
              )
            ) : (
              <div className="nav-tree-section">
                <PortalNavIcon name={n.icon} className="nav-tree-icon" />
                <span className="nav-tree-section-label">{n.label}</span>
              </div>
            )}
            {n.children && n.children.length > 0 && (
              <NavBranch nodes={n.children} depth={depth + 1} t={t} />
            )}
          </li>
        ))}
    </ul>
  );
}

type Props = {
  items: NavigationItemDto[];
  collapsed: boolean;
  onExpand?: () => void;
};

export function NavSidebar({ items, collapsed, onExpand }: Props) {
  const { t } = useTranslation();

  if (collapsed) {
    return (
      <Box
        h="100%"
        pt="xs"
        display="flex"
        style={{ justifyContent: 'center' }}
        role="navigation"
        aria-label={t('nav.moduleNavAria')}
      >
        <ActionIcon
          variant="default"
          onClick={onExpand}
          title={t('nav.expandNav')}
          aria-label={t('nav.expandNav')}
        >
          <IconChevronsRight size={18} stroke={1.5} aria-hidden />
        </ActionIcon>
      </Box>
    );
  }

  return (
    <ScrollArea h="100%" type="scroll" scrollbarSize={8} role="navigation" aria-label={t('nav.moduleNavAria')}>
      <Box p="xs" pr="sm">
        <NavBranch nodes={items} depth={0} t={t} />
      </Box>
    </ScrollArea>
  );
}

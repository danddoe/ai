import { NavLink } from 'react-router-dom';
import type { NavigationItemDto } from '../api/schemas';
import { PortalNavIcon } from '../ui/portalNavIcons';

function NavBranch({ nodes, depth }: { nodes: NavigationItemDto[]; depth: number }) {
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
                </NavLink>
              )
            ) : (
              <div className="nav-tree-section">
                <PortalNavIcon name={n.icon} className="nav-tree-icon" />
                <span className="nav-tree-section-label">{n.label}</span>
              </div>
            )}
            {n.children && n.children.length > 0 && <NavBranch nodes={n.children} depth={depth + 1} />}
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
  if (collapsed) {
    return (
      <aside className="app-sidebar app-sidebar-collapsed" aria-label="Module navigation">
        <button type="button" className="app-sidebar-expand" onClick={onExpand} title="Expand navigation">
          »
        </button>
      </aside>
    );
  }

  return (
    <aside className="app-sidebar" aria-label="Module navigation">
      <div className="app-sidebar-scroll">
        <NavBranch nodes={items} depth={0} />
      </div>
    </aside>
  );
}

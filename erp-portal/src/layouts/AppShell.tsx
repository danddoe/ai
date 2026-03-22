import { useEffect, useMemo, useState } from 'react';
import { Link, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider';
import { GlobalSearch } from '../components/GlobalSearch';
import { NavSidebar } from '../components/NavSidebar';
import { usePortalNavigation } from '../hooks/usePortalNavigation';

const COLLAPSE_KEY = 'erp_nav_collapsed';

/** Full-width builder chrome: hide module sidebar, show builder badge. */
function layoutBuilderMode(pathname: string): 'form' | 'list' | null {
  if (/\/entities\/[^/]+\/layouts\/[^/]+$/.test(pathname)) return 'form';
  if (/\/entities\/[^/]+\/list-views\//.test(pathname)) return 'list';
  return null;
}

export function AppShell() {
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

  const shellClass = useMemo(() => `app-shell${builderMode ? ' app-shell--builder' : ''}`, [builderMode]);

  return (
    <div className={shellClass}>
      <header className="app-header">
        <div className="app-header-left">
          {!builderMode && (
            <button
              type="button"
              className="app-header-icon-btn"
              onClick={() => setCollapsed((c) => !c)}
              aria-expanded={!collapsed}
              aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
            >
              ☰
            </button>
          )}
          <Link to="/home" className="app-logo">
            ERP
          </Link>
          {builderMode === 'form' && (
            <span className="app-header-mode-badge" title="Form builder — module nav hidden">
              Form builder
            </span>
          )}
          {builderMode === 'list' && (
            <span className="app-header-mode-badge" title="List view designer — module nav hidden">
              List designer
            </span>
          )}
        </div>
        <div className="app-header-center">
          <GlobalSearch />
        </div>
        <div className="app-header-right">
          <button type="button" className="btn btn-ghost btn-sm" onClick={() => void logout()}>
            Log out
          </button>
        </div>
      </header>
      <div className="app-body">
        {sidebarVisible && (
          <NavSidebar
            items={items}
            collapsed={collapsed}
            onExpand={() => setCollapsed(false)}
          />
        )}
        <main className={`app-main${builderMode ? ' app-main--builder' : ''}`}>
          {navState.status === 'error' && builderMode === null && (
            <p className="app-nav-warn" role="status">
              Could not load navigation: {navState.message}
            </p>
          )}
          <Outlet />
        </main>
      </div>
    </div>
  );
}

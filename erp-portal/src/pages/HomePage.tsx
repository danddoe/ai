import { Link } from 'react-router-dom';
import { usePortalNavigation } from '../hooks/usePortalNavigation';
import { useAuth } from '../auth/AuthProvider';

function formatCategory(key: string | null): string {
  if (!key) return 'General';
  return key.replace(/_/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase());
}

export function HomePage() {
  const { items, state } = usePortalNavigation();
  const { canCreatePortalNavItem, canManageGlobalNavigation } = useAuth();

  return (
    <div className="page-shell page-shell-wide">
      <h1 className="page-title">Home</h1>
      <p className="page-sub">Welcome to the ERP portal. Use the search bar (Ctrl+K / ⌘K) to jump anywhere, or open a module below.</p>

      {state.status === 'error' && (
        <p role="alert" className="text-error">
          Navigation could not be loaded. You can still open{' '}
          <Link to="/entities">Entities</Link>.
        </p>
      )}

      <section className="home-modules" aria-label="Modules">
        {items.map((root) => (
          <div key={root.id} className="home-module-card">
            <h2 className="home-module-title">
              {root.label}
              {root.categoryKey && (
                <span className="home-module-cat">{formatCategory(root.categoryKey)}</span>
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

      <p className="builder-muted" style={{ marginTop: 24 }}>
        {canCreatePortalNavItem && (
          <>
            <Link to="/ui/create">Create UI (template → entity → nav) →</Link>
            {' · '}
          </>
        )}
        {(canCreatePortalNavItem || canManageGlobalNavigation) && (
          <>
            <Link to="/settings/navigation-items">Navigation items →</Link>
            {' · '}
          </>
        )}
        <Link to="/entities">Browse all entities →</Link>
        {' · '}
        <Link to="/lending/hybrid-demo">Hybrid loan (core + EAV) demo →</Link>
      </p>
    </div>
  );
}

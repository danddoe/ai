import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import {
  apiBaseUrl,
  setAccessToken as setMemToken,
  setOnAccessTokenRefreshed,
} from '../api/client';
import { clearPortalNavigationCache } from '../hooks/usePortalNavigation';
import { clearLegacyStoredTokens } from './tokenStorage';
import {
  canCreatePortalNavItem,
  canManageGlobalNavigation,
  canPiiRead,
  canRecordsRead,
  canRecordsWrite,
  canSchemaRead,
  canSchemaWrite,
  parseJwtPermissions,
  parseJwtTenantId,
} from './jwtPermissions';

type AuthContextValue = {
  accessToken: string | null;
  /**
   * Becomes true after the first attempt to restore a session via POST /auth/refresh (httpOnly cookie).
   * Route guards should wait for this so a full page reload does not send users to login while the cookie is still valid.
   */
  sessionRestored: boolean;
  /** IAM JWT `tenant_id`; empty if missing from token. */
  tenantId: string;
  /** IAM JWT `permissions` claim; UI hints only. */
  permissions: string[];
  canSchemaWrite: boolean;
  canSchemaRead: boolean;
  canRecordsRead: boolean;
  canRecordsWrite: boolean;
  canPiiRead: boolean;
  /** `portal:navigation:write` or `entity_builder:schema:write` — create/edit portal nav items. */
  canCreatePortalNavItem: boolean;
  /** Global nav admin (IAM). */
  canManageGlobalNavigation: boolean;
  login: (tenantSlugOrId: string, email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [accessToken, setAccessTokenState] = useState<string | null>(null);
  const [sessionRestored, setSessionRestored] = useState(false);

  useEffect(() => {
    clearLegacyStoredTokens();
  }, []);

  useEffect(() => {
    setOnAccessTokenRefreshed((t) => {
      setAccessTokenState(t);
    });
    return () => setOnAccessTokenRefreshed(null);
  }, []);

  const setBoth = useCallback((t: string | null) => {
    setMemToken(t);
    setAccessTokenState(t);
  }, []);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const res = await fetch(`${apiBaseUrl()}/auth/refresh`, {
          method: 'POST',
          credentials: 'include',
          headers: { 'Content-Type': 'application/json' },
          body: '{}',
        });
        if (cancelled) return;
        if (res.ok) {
          const data = (await res.json()) as { accessToken?: string };
          if (data.accessToken) {
            setBoth(data.accessToken);
          }
        }
      } catch {
        /* offline or CORS — leave logged out */
      } finally {
        if (!cancelled) setSessionRestored(true);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [setBoth]);

  const login = useCallback(
    async (tenantSlugOrId: string, email: string, password: string) => {
      const res = await fetch(`${apiBaseUrl()}/auth/login`, {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ tenantSlugOrId, email, password }),
      });
      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        throw new Error((err as { error?: string }).error || `Login failed (${res.status})`);
      }
      const data = (await res.json()) as { accessToken: string };
      if (!data.accessToken) {
        throw new Error('No access token in response');
      }
      setBoth(data.accessToken);
    },
    [setBoth]
  );

  const logout = useCallback(async () => {
    await fetch(`${apiBaseUrl()}/auth/logout`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: '{}',
    });
    clearLegacyStoredTokens();
    clearPortalNavigationCache();
    setBoth(null);
  }, [setBoth]);

  const permissions = useMemo(() => parseJwtPermissions(accessToken), [accessToken]);
  const tenantId = useMemo(() => parseJwtTenantId(accessToken), [accessToken]);
  const schemaWrite = useMemo(() => canSchemaWrite(permissions), [permissions]);
  const schemaRead = useMemo(() => canSchemaRead(permissions), [permissions]);
  const recordsRead = useMemo(() => canRecordsRead(permissions), [permissions]);
  const recordsWrite = useMemo(() => canRecordsWrite(permissions), [permissions]);
  const piiRead = useMemo(() => canPiiRead(permissions), [permissions]);
  const portalNavCreate = useMemo(() => canCreatePortalNavItem(permissions), [permissions]);
  const globalNavAdmin = useMemo(() => canManageGlobalNavigation(permissions), [permissions]);

  const value = useMemo(
    () => ({
      accessToken,
      sessionRestored,
      tenantId,
      permissions,
      canSchemaWrite: schemaWrite,
      canSchemaRead: schemaRead,
      canRecordsRead: recordsRead,
      canRecordsWrite: recordsWrite,
      canPiiRead: piiRead,
      canCreatePortalNavItem: portalNavCreate,
      canManageGlobalNavigation: globalNavAdmin,
      login,
      logout,
    }),
    [
      accessToken,
      sessionRestored,
      tenantId,
      permissions,
      schemaWrite,
      schemaRead,
      recordsRead,
      recordsWrite,
      piiRead,
      portalNavCreate,
      globalNavAdmin,
      login,
      logout,
    ]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth outside AuthProvider');
  }
  return ctx;
}

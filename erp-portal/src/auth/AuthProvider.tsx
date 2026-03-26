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
  localeRequestHeaders,
  publishAccessTokenToOtherTabs,
  refreshAccessToken,
  setAccessToken as setMemToken,
  setOnAccessTokenRefreshed,
  setOnSessionInvalidated,
} from '../api/client';
import { applyPreferredLocaleFromServer } from '../i18n/apiLocale';
import { clearPortalNavigationCache } from '../hooks/usePortalNavigation';
import { clearLegacyStoredTokens } from './tokenStorage';
import { fetchPortalBootstrap, type PortalBootstrapPayload } from '../api/portalBootstrap';
import {
  canCreatePortalNavItem,
  canManageGlobalNavigation,
  canPlatformSchemaWrite,
  canPiiRead,
  canRecordsRead,
  canRecordsWrite,
  canRunSampleTenantSeed,
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
  /** Full platform schema write (catalog sync, DDL apply, editing catalog entities). */
  canPlatformSchemaWrite: boolean;
  canSchemaRead: boolean;
  canRecordsRead: boolean;
  canRecordsWrite: boolean;
  canPiiRead: boolean;
  /** `portal:navigation:write` or `entity_builder:schema:write` — create/edit portal nav items. */
  canCreatePortalNavItem: boolean;
  /** Global nav admin (IAM). */
  canManageGlobalNavigation: boolean;
  /** Dev: seed sample tenant via IAM APIs (requires tenants/security/superadmin IAM permission). */
  canRunSampleTenantSeed: boolean;
  /**
   * Resolved from `GET /v1/portal/bootstrap` (custom hostname / CNAME). Empty strings when unknown.
   * Approach A: UX defaults only; authorization remains JWT tenant + permissions.
   */
  portalBootstrap: PortalBootstrapPayload | null;
  portalBootstrapLoaded: boolean;
  login: (tenantSlugOrId: string, email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [accessToken, setAccessTokenState] = useState<string | null>(null);
  const [sessionRestored, setSessionRestored] = useState(false);
  const [portalBootstrap, setPortalBootstrap] = useState<PortalBootstrapPayload | null>(null);
  const [portalBootstrapLoaded, setPortalBootstrapLoaded] = useState(false);

  useEffect(() => {
    clearLegacyStoredTokens();
  }, []);

  useEffect(() => {
    let cancelled = false;
    const override = import.meta.env.VITE_PORTAL_HOSTNAME_OVERRIDE as string | undefined;
    (async () => {
      try {
        const data = await fetchPortalBootstrap(override?.trim() || undefined);
        if (!cancelled) {
          setPortalBootstrap(data);
        }
      } catch {
        if (!cancelled) {
          setPortalBootstrap(null);
        }
      } finally {
        if (!cancelled) {
          setPortalBootstrapLoaded(true);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
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

  const invalidateLocalSession = useCallback(() => {
    clearLegacyStoredTokens();
    clearPortalNavigationCache();
    setBoth(null);
  }, [setBoth]);

  useEffect(() => {
    setOnSessionInvalidated(() => invalidateLocalSession);
    return () => setOnSessionInvalidated(null);
  }, [invalidateLocalSession]);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const result = await refreshAccessToken();
        if (cancelled) return;
        if (result.kind === 'ok') {
          setBoth(result.accessToken);
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
        headers: { 'Content-Type': 'application/json', ...localeRequestHeaders() },
        body: JSON.stringify({ tenantSlugOrId, email, password }),
      });
      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        throw new Error((err as { error?: string }).error || `Login failed (${res.status})`);
      }
      const data = (await res.json()) as { accessToken: string; preferredLocale?: string | null };
      if (!data.accessToken) {
        throw new Error('No access token in response');
      }
      applyPreferredLocaleFromServer(data.preferredLocale);
      setBoth(data.accessToken);
      publishAccessTokenToOtherTabs(data.accessToken, data.preferredLocale);
    },
    [setBoth]
  );

  const logout = useCallback(async () => {
    await fetch(`${apiBaseUrl()}/auth/logout`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json', ...localeRequestHeaders() },
      body: '{}',
    });
    clearLegacyStoredTokens();
    clearPortalNavigationCache();
    setBoth(null);
  }, [setBoth]);

  const permissions = useMemo(() => parseJwtPermissions(accessToken), [accessToken]);
  const tenantId = useMemo(() => parseJwtTenantId(accessToken), [accessToken]);
  const schemaWrite = useMemo(() => canSchemaWrite(permissions), [permissions]);
  const platformSchemaWrite = useMemo(() => canPlatformSchemaWrite(permissions), [permissions]);
  const schemaRead = useMemo(() => canSchemaRead(permissions), [permissions]);
  const recordsRead = useMemo(() => canRecordsRead(permissions), [permissions]);
  const recordsWrite = useMemo(() => canRecordsWrite(permissions), [permissions]);
  const piiRead = useMemo(() => canPiiRead(permissions), [permissions]);
  const portalNavCreate = useMemo(() => canCreatePortalNavItem(permissions), [permissions]);
  const globalNavAdmin = useMemo(() => canManageGlobalNavigation(permissions), [permissions]);
  const sampleSeed = useMemo(() => canRunSampleTenantSeed(permissions), [permissions]);

  const value = useMemo(
    () => ({
      accessToken,
      sessionRestored,
      tenantId,
      permissions,
      canSchemaWrite: schemaWrite,
      canPlatformSchemaWrite: platformSchemaWrite,
      canSchemaRead: schemaRead,
      canRecordsRead: recordsRead,
      canRecordsWrite: recordsWrite,
      canPiiRead: piiRead,
      canCreatePortalNavItem: portalNavCreate,
      canManageGlobalNavigation: globalNavAdmin,
      canRunSampleTenantSeed: sampleSeed,
      portalBootstrap,
      portalBootstrapLoaded,
      login,
      logout,
    }),
    [
      accessToken,
      sessionRestored,
      tenantId,
      permissions,
      schemaWrite,
      platformSchemaWrite,
      schemaRead,
      recordsRead,
      recordsWrite,
      piiRead,
      portalNavCreate,
      globalNavAdmin,
      sampleSeed,
      portalBootstrap,
      portalBootstrapLoaded,
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

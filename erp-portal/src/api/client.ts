import { isAccessTokenExpiredOrNearing } from '../auth/jwtPermissions';
import { applyPreferredLocaleFromServer, getApiLocale } from '../i18n/apiLocale';

/** Proactively refresh the bearer this long before JWT {@code exp} (default IAM access token = 15 min). */
const ACCESS_TOKEN_PROACTIVE_REFRESH_LEEWAY_MS = 120_000;

const AUTH_REFRESH_LOCK_NAME = 'erp_portal_auth_refresh';
const AUTH_BROADCAST_NAME = 'erp_portal_auth';

let accessTokenMemory: string | null = null;
let onTokenRefreshed: ((token: string) => void) | null = null;
/** Called when refresh fails after a 401/403 so the app can clear JWT state and show login. */
let onSessionInvalidated: (() => void) | null = null;

/** Set when any tab refreshes (this tab or via BroadcastChannel). Used to skip duplicate POST /auth/refresh. */
let lastAuthRefreshBroadcastAt = 0;

let refreshInFlight: Promise<RefreshAccessTokenResult> | null = null;

const authBroadcast: BroadcastChannel | null =
  typeof BroadcastChannel !== 'undefined' ? new BroadcastChannel(AUTH_BROADCAST_NAME) : null;

if (authBroadcast) {
  authBroadcast.addEventListener('message', (ev: MessageEvent) => {
    const d = ev.data as
      | { type?: string; accessToken?: string; preferredLocale?: string | null; ts?: number }
      | undefined;
    if (d?.type !== 'auth_access_token' || typeof d.accessToken !== 'string' || !d.accessToken) {
      return;
    }
    const ts = typeof d.ts === 'number' ? d.ts : Date.now();
    lastAuthRefreshBroadcastAt = ts;
    applyPreferredLocaleFromServer(d.preferredLocale);
    accessTokenMemory = d.accessToken;
    onTokenRefreshed?.(d.accessToken);
  });
}

class RefreshAuthFailure extends Error {
  constructor() {
    super('refresh_auth_failed');
    this.name = 'RefreshAuthFailure';
  }
}

export type RefreshAccessTokenResult =
  | { kind: 'ok'; accessToken: string }
  | { kind: 'auth_failed' }
  | { kind: 'network_error' };

export function setOnAccessTokenRefreshed(cb: ((token: string) => void) | null) {
  onTokenRefreshed = cb;
}

export function setOnSessionInvalidated(cb: (() => void) | null) {
  onSessionInvalidated = cb;
}

export function setAccessToken(token: string | null) {
  accessTokenMemory = token;
}

export function getAccessToken() {
  return accessTokenMemory;
}

export function apiBaseUrl() {
  const base = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8000';
  return base.replace(/\/$/, '');
}

/** Sent on gateway/API calls so services can resolve localized metadata. */
export function localeRequestHeaders(): Record<string, string> {
  const lang = getApiLocale();
  return {
    'Accept-Language': lang,
    'X-User-Locale': lang,
  };
}

/**
 * Single-flight, cross-tab coordinated POST /auth/refresh (Web Locks + BroadcastChannel).
 * Use for session restore and after 401 — avoids refresh-token rotation races between tabs.
 */
export async function refreshAccessToken(): Promise<RefreshAccessTokenResult> {
  if (!refreshInFlight) {
    refreshInFlight = runCoordinatedRefresh().finally(() => {
      refreshInFlight = null;
    });
  }
  return refreshInFlight;
}

async function runCoordinatedRefresh(): Promise<RefreshAccessTokenResult> {
  const waitStartedAt = typeof performance !== 'undefined' ? performance.now() : 0;
  try {
    if (typeof navigator !== 'undefined' && navigator.locks?.request) {
      const token = await navigator.locks.request(
        AUTH_REFRESH_LOCK_NAME,
        { mode: 'exclusive' },
        async () => {
          if (lastAuthRefreshBroadcastAt > waitStartedAt && accessTokenMemory) {
            return accessTokenMemory;
          }
          return await fetchRefreshAndApply();
        }
      );
      return { kind: 'ok', accessToken: token };
    }
    const token = await fetchRefreshAndApply();
    return { kind: 'ok', accessToken: token };
  } catch (e) {
    if (e instanceof RefreshAuthFailure) {
      return { kind: 'auth_failed' };
    }
    return { kind: 'network_error' };
  }
}

async function fetchRefreshAndApply(): Promise<string> {
  let res: Response;
  try {
    res = await fetch(`${apiBaseUrl()}/auth/refresh`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json', ...localeRequestHeaders() },
      body: '{}',
    });
  } catch {
    throw new Error('network');
  }
  if (res.status === 401 || res.status === 403) {
    throw new RefreshAuthFailure();
  }
  if (!res.ok) {
    throw new Error('network');
  }
  const data = (await res.json()) as { accessToken: string; preferredLocale?: string | null };
  if (!data.accessToken) {
    throw new RefreshAuthFailure();
  }
  applyPreferredLocaleFromServer(data.preferredLocale);
  accessTokenMemory = data.accessToken;
  onTokenRefreshed?.(data.accessToken);
  const ts = Date.now();
  lastAuthRefreshBroadcastAt = ts;
  authBroadcast?.postMessage({
    type: 'auth_access_token',
    accessToken: data.accessToken,
    preferredLocale: data.preferredLocale,
    ts,
  });
  return data.accessToken;
}

function loginPageUrlWithQuery(query: Record<string, string>): string {
  const root = (import.meta.env.BASE_URL || '/').replace(/\/+$/, '');
  const path = `${root}/login`.replace(/\/{2,}/g, '/');
  const q = new URLSearchParams(query);
  return `${window.location.origin}${path}?${q.toString()}`;
}

/**
 * Hard navigation so the app does not stay on a screen showing "Request failed (403)" after refresh fails.
 */
function redirectToLoginSessionExpired(): void {
  if (typeof window === 'undefined') return;
  try {
    const returnTo = `${window.location.pathname}${window.location.search}`;
    window.location.replace(
      loginPageUrlWithQuery({ session: 'expired', returnTo: returnTo || '/home' })
    );
  } catch {
    /* ignore */
  }
}

/**
 * While the refresh cookie is valid, obtain a new access token before the current one expires.
 * @returns false if the session is invalid and the browser is being sent to login.
 */
async function ensureAccessTokenFreshForRequest(): Promise<boolean> {
  if (!accessTokenMemory) return true;
  if (!isAccessTokenExpiredOrNearing(accessTokenMemory, ACCESS_TOKEN_PROACTIVE_REFRESH_LEEWAY_MS)) {
    return true;
  }
  const refreshed = await refreshAccessToken();
  if (refreshed.kind === 'ok') {
    return true;
  }
  if (refreshed.kind === 'auth_failed') {
    accessTokenMemory = null;
    onSessionInvalidated?.();
    redirectToLoginSessionExpired();
    return false;
  }
  return true;
}

/** Notify other tabs after login (same-origin BroadcastChannel). */
export function publishAccessTokenToOtherTabs(accessToken: string, preferredLocale?: string | null) {
  const ts = Date.now();
  lastAuthRefreshBroadcastAt = ts;
  authBroadcast?.postMessage({
    type: 'auth_access_token',
    accessToken,
    preferredLocale,
    ts,
  });
}

/**
 * Authenticated fetch via API gateway. Sends httpOnly refresh cookie.
 * Retries once after coordinated POST /auth/refresh.
 */
export async function apiFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const url = path.startsWith('http') ? path : `${apiBaseUrl()}${path.startsWith('/') ? path : `/${path}`}`;
  const skipRefresh = path.includes('/auth/refresh') || path.includes('/auth/login');

  if (!skipRefresh) {
    const continuing = await ensureAccessTokenFreshForRequest();
    if (!continuing) {
      return new Response(null, { status: 401, statusText: 'Session expired' });
    }
  }

  const headers = new Headers(init.headers);
  const loc = localeRequestHeaders();
  headers.set('Accept-Language', loc['Accept-Language']);
  headers.set('X-User-Locale', loc['X-User-Locale']);
  if (accessTokenMemory) {
    headers.set('Authorization', `Bearer ${accessTokenMemory}`);
  }

  const doFetch = () =>
    fetch(url, {
      ...init,
      headers,
      credentials: 'include',
    });

  let res = await doFetch();

  if (!skipRefresh && (res.status === 401 || res.status === 403)) {
    const refreshed = await refreshAccessToken();
    if (refreshed.kind === 'ok') {
      headers.set('Authorization', `Bearer ${refreshed.accessToken}`);
      res = await fetch(url, { ...init, headers, credentials: 'include' });
    } else if (refreshed.kind === 'auth_failed') {
      accessTokenMemory = null;
      onSessionInvalidated?.();
      redirectToLoginSessionExpired();
      return new Response(null, { status: 401, statusText: 'Session expired' });
    }
  }

  return res;
}

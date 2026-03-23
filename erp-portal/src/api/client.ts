let accessTokenMemory: string | null = null;
let onTokenRefreshed: ((token: string) => void) | null = null;
/** Called when refresh fails after a 401/403 so the app can clear JWT state and show login. */
let onSessionInvalidated: (() => void) | null = null;

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

/**
 * Authenticated fetch via API gateway. Sends httpOnly refresh cookie.
 * Retries once after POST /auth/refresh (cookie only, no body token).
 */
export async function apiFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const url = path.startsWith('http') ? path : `${apiBaseUrl()}${path.startsWith('/') ? path : `/${path}`}`;
  const headers = new Headers(init.headers);
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

  const skipRefresh = path.includes('/auth/refresh') || path.includes('/auth/login');
  if (!skipRefresh && (res.status === 401 || res.status === 403)) {
    let refreshRes: Response;
    try {
      refreshRes = await fetch(`${apiBaseUrl()}/auth/refresh`, {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: '{}',
      });
    } catch {
      return res;
    }
    if (refreshRes.ok) {
      const data = (await refreshRes.json()) as { accessToken: string };
      accessTokenMemory = data.accessToken;
      onTokenRefreshed?.(data.accessToken);
      headers.set('Authorization', `Bearer ${accessTokenMemory}`);
      res = await fetch(url, { ...init, headers, credentials: 'include' });
    } else if (refreshRes.status === 401 || refreshRes.status === 403) {
      accessTokenMemory = null;
      onSessionInvalidated?.();
    }
  }

  return res;
}

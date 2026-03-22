let accessTokenMemory: string | null = null;
let onTokenRefreshed: ((token: string) => void) | null = null;

export function setOnAccessTokenRefreshed(cb: ((token: string) => void) | null) {
  onTokenRefreshed = cb;
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

  if (res.status === 401 && !path.includes('/auth/refresh') && !path.includes('/auth/login')) {
    const refreshRes = await fetch(`${apiBaseUrl()}/auth/refresh`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: '{}',
    });
    if (refreshRes.ok) {
      const data = (await refreshRes.json()) as { accessToken: string };
      accessTokenMemory = data.accessToken;
      onTokenRefreshed?.(data.accessToken);
      headers.set('Authorization', `Bearer ${accessTokenMemory}`);
      res = await fetch(url, { ...init, headers, credentials: 'include' });
    }
  }

  return res;
}

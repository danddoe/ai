import { useCallback, useEffect, useState } from 'react';
import { getNavigation, type NavigationItemDto, type NavigationResponse } from '../api/schemas';

type State =
  | { status: 'idle' | 'loading' }
  | { status: 'ok'; data: NavigationResponse }
  | { status: 'error'; message: string };

let cached: NavigationResponse | null = null;

export function clearPortalNavigationCache() {
  cached = null;
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent('erp-portal-nav-cache-cleared'));
  }
}

/**
 * Loads IAM portal navigation once per session (cached). Call refresh() after login if needed.
 */
export function usePortalNavigation() {
  const [state, setState] = useState<State>(() =>
    cached ? { status: 'ok', data: cached } : { status: 'idle' }
  );

  const load = useCallback(async () => {
    setState({ status: 'loading' });
    try {
      const data = await getNavigation();
      cached = data;
      setState({ status: 'ok', data });
    } catch (e) {
      setState({ status: 'error', message: e instanceof Error ? e.message : 'Failed to load navigation' });
    }
  }, []);

  useEffect(() => {
    if (!cached && state.status === 'idle') {
      void load();
    }
  }, [load, state.status]);

  useEffect(() => {
    const onCleared = () => {
      void load();
    };
    window.addEventListener('erp-portal-nav-cache-cleared', onCleared);
    return () => window.removeEventListener('erp-portal-nav-cache-cleared', onCleared);
  }, [load]);

  const refresh = useCallback(() => {
    cached = null;
    void load();
  }, [load]);

  return { state, load, refresh, items: state.status === 'ok' ? state.data.items : ([] as NavigationItemDto[]) };
}

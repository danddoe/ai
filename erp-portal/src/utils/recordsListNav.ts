import type { NavigationItemDto } from '../api/schemas';

const RECORDS_PATH_RE = /^\/entities\/([0-9a-fA-F-]{36})\/records\/?$/;

export function normalizeEntityIdForMatch(id: string): string {
  return id.trim().toLowerCase();
}

export function flattenNavigationItems(items: NavigationItemDto[]): NavigationItemDto[] {
  const out: NavigationItemDto[] = [];
  function walk(nodes: NavigationItemDto[]) {
    for (const n of nodes) {
      out.push(n);
      if (n.children?.length) walk(n.children);
    }
  }
  walk(items);
  return out;
}

/** Path segment only (no query). Entity id normalized to lowercase for comparison. */
export function parseEntityIdFromRecordsRoutePath(routePath: string | null): string | null {
  if (!routePath) return null;
  const path = routePath.split('?')[0].replace(/\/+$/, '') || '/';
  const m = path.match(RECORDS_PATH_RE);
  return m ? normalizeEntityIdForMatch(m[1]) : null;
}

/** Sidebar links that open this entity's record list (not /new). */
export function navItemsForEntityRecordsList(items: NavigationItemDto[], entityId: string): NavigationItemDto[] {
  const want = normalizeEntityIdForMatch(entityId);
  return flattenNavigationItems(items).filter((n) => {
    if ((n.type ?? '').toLowerCase() !== 'internal' || !n.routePath) return false;
    const inPath = parseEntityIdFromRecordsRoutePath(n.routePath);
    return inPath === want;
  });
}

const UUID_RE =
  /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/;

export type ListViewQueryState = {
  /** Persisted list view id; when set, IAM route uses `view=` (column config lives in entity-builder). */
  viewId: string | null;
  cols: string[];
  inlineSlugs: string[];
  showRowActions: boolean;
};

export function parseListViewQueryFromRoutePath(routePath: string): ListViewQueryState {
  const qs = routePath.includes('?') ? routePath.split('?')[1] ?? '' : '';
  const p = new URLSearchParams(qs);
  const rawView = (p.get('view') ?? '').trim();
  const viewId = UUID_RE.test(rawView) ? rawView : null;
  const cols = (p.get('cols') ?? '')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);
  const inlineSlugs = (p.get('inline') ?? '')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);
  const showRowActions = p.get('actions') !== '0';
  return { viewId, cols, inlineSlugs, showRowActions };
}

/** Query string without `?`, or empty. */
export function buildRecordsListQueryString(state: ListViewQueryState): string {
  const p = new URLSearchParams();
  if (state.viewId && UUID_RE.test(state.viewId)) {
    p.set('view', state.viewId);
    return p.toString();
  }
  if (state.cols.length > 0) p.set('cols', state.cols.join(','));
  const inline = state.cols.filter((s) => state.inlineSlugs.includes(s));
  if (inline.length > 0) p.set('inline', inline.join(','));
  if (!state.showRowActions) p.set('actions', '0');
  return p.toString();
}

export function recordsListPath(entityId: string, state: ListViewQueryState): string {
  const eid = normalizeEntityIdForMatch(entityId);
  const q = buildRecordsListQueryString(state);
  return q ? `/entities/${eid}/records?${q}` : `/entities/${eid}/records`;
}

/** Merge saved list config with session params (q, page, pageSize). */
export function mergeRecordsListLocation(
  entityId: string,
  state: ListViewQueryState,
  preserveParams: URLSearchParams
): string {
  const eid = normalizeEntityIdForMatch(entityId);
  const merged = new URLSearchParams(buildRecordsListQueryString(state));
  for (const key of ['q', 'page', 'pageSize'] as const) {
    const v = preserveParams.get(key);
    if (v != null && v !== '') merged.set(key, v);
  }
  const qs = merged.toString();
  return qs ? `/entities/${eid}/records?${qs}` : `/entities/${eid}/records`;
}

/** Path with only `view=` (for quick links). */
export function recordsListPathForViewId(entityId: string, viewId: string): string {
  const eid = normalizeEntityIdForMatch(entityId);
  return `/entities/${eid}/records?view=${viewId.trim()}`;
}

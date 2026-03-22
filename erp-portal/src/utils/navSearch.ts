import type { NavigationItemDto } from '../api/schemas';

export type NavSearchHit = {
  id: string;
  title: string;
  path: string;
  subtitle?: string | null;
  group: string;
  kind: 'nav';
  keywords: string[];
};

function walkNav(items: NavigationItemDto[], inheritedCategory: string | null, out: NavSearchHit[]) {
  for (const n of items) {
    const group = n.categoryKey || inheritedCategory || 'General';
    const kw = n.searchKeywords ?? [];
    if (n.routePath && (n.type === 'internal' || n.type === 'external')) {
      out.push({
        id: n.id,
        title: n.label,
        path: n.routePath,
        subtitle: n.description,
        group,
        kind: 'nav',
        keywords: kw,
      });
    }
    if (n.children?.length) {
      walkNav(n.children, group, out);
    }
  }
}

export function flattenNavForSearch(items: NavigationItemDto[]): NavSearchHit[] {
  const out: NavSearchHit[] = [];
  walkNav(items, null, out);
  return out;
}

export function matchesQuery(
  q: string,
  title: string,
  subtitle: string | null | undefined,
  keywords: string[]
): boolean {
  const s = q.trim().toLowerCase();
  if (!s) return true;
  if (title.toLowerCase().includes(s)) return true;
  if (subtitle?.toLowerCase().includes(s)) return true;
  return keywords.some((k) => k.toLowerCase().includes(s));
}

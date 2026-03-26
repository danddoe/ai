/**
 * Module bucket keys for portal navigation (and entities), lowercase snake_case.
 * Keep in sync with {@code entity-builder/.../EntityCategoryKeys.ALLOWED}.
 * IAM does not enforce this list on nav PATCH; entity-builder validates entity categoryKey against the Java set.
 */
export const PORTAL_NAV_CATEGORY_KEYS = [
  'entity_builder',
  'accounting',
  'accounts_payable',
  'general_ledger',
  'accounts_receivable',
  'inventory',
  'hr',
  'security',
  'lending',
  'master_data',
] as const;

export function formatCategoryKeyLabel(key: string): string {
  return key.replace(/_/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase());
}

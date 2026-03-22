/**
 * Clears legacy keys from older builds (access/refresh in sessionStorage).
 * Access token lives in memory only; refresh is httpOnly cookie (IAM + gateway).
 */
const LEGACY_ACCESS = 'erp_access_token';
const LEGACY_REFRESH = 'erp_refresh_token';

export function clearLegacyStoredTokens() {
  try {
    sessionStorage.removeItem(LEGACY_ACCESS);
    sessionStorage.removeItem(LEGACY_REFRESH);
  } catch {
    /* private mode etc. */
  }
}

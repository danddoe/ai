import i18n from './config';
import { FALLBACK_LOCALE, PORTAL_LOCALE_STORAGE_KEY } from './constants';

/** After login/refresh, align portal i18n with IAM user/tenant preference. */
export function applyPreferredLocaleFromServer(raw: string | null | undefined): void {
  if (raw == null || !raw.trim()) {
    return;
  }
  const code = raw.trim().split(/[-_]/)[0].toLowerCase();
  if (!code) {
    return;
  }
  void i18n.changeLanguage(code);
}

function normalizeLocales(raw: string): string {
  const t = raw.trim();
  if (!t) return FALLBACK_LOCALE;
  const [base] = t.split(/[-_]/);
  return base ? base.toLowerCase() : FALLBACK_LOCALE;
}

/**
 * Language tag for API metadata resolution (Accept-Language / X-User-Locale).
 * Syncs with i18next after init; falls back to stored value or navigator.
 */
export function getApiLocale(): string {
  try {
    const stored = localStorage.getItem(PORTAL_LOCALE_STORAGE_KEY);
    if (stored) return normalizeLocales(stored);
  } catch {
    /* private mode */
  }
  if (i18n.isInitialized) {
    const lg = i18n.resolvedLanguage || i18n.language;
    if (lg) return normalizeLocales(lg);
  }
  if (typeof navigator !== 'undefined' && navigator.language) {
    return normalizeLocales(navigator.language);
  }
  return FALLBACK_LOCALE;
}

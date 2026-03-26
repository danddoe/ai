package com.erp.entitybuilder.web;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Locale;

/**
 * Resolves a primary language tag for localized metadata (field labels, status labels, etc.).
 * Convention: {@code X-User-Locale} overrides {@code Accept-Language}; fallback {@code en}.
 */
public final class RequestLocaleResolver {

    public static final String HEADER_USER_LOCALE = "X-User-Locale";

    private RequestLocaleResolver() {}

    public static String resolveLanguage(HttpServletRequest request) {
        String explicit = request.getHeader(HEADER_USER_LOCALE);
        if (explicit != null && !explicit.isBlank()) {
            return normalizeLanguage(explicit);
        }
        String accept = request.getHeader("Accept-Language");
        if (accept != null && !accept.isBlank()) {
            try {
                List<Locale.LanguageRange> ranges = Locale.LanguageRange.parse(accept);
                if (!ranges.isEmpty()) {
                    return normalizeLanguage(ranges.get(0).getRange());
                }
            } catch (IllegalArgumentException ignored) {
                /* fall through */
            }
        }
        return "en";
    }

    public static String normalizeLanguage(String raw) {
        if (raw == null || raw.isBlank()) {
            return "en";
        }
        String t = raw.trim().replace('_', '-');
        int delim = t.indexOf('-');
        String primary = delim > 0 ? t.substring(0, delim) : t;
        if (primary.isBlank()) {
            return "en";
        }
        return primary.toLowerCase(Locale.ROOT);
    }
}

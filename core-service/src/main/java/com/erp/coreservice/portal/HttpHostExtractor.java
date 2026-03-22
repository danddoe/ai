package com.erp.coreservice.portal;

import jakarta.servlet.http.HttpServletRequest;

public final class HttpHostExtractor {

    private HttpHostExtractor() {
    }

    /**
     * Prefer {@code X-Forwarded-Host} (first entry) when present, else {@code Host}.
     */
    public static String effectiveHost(HttpServletRequest request, String queryHostnameOverride) {
        if (queryHostnameOverride != null && !queryHostnameOverride.isBlank()) {
            return queryHostnameOverride.trim();
        }
        String forwarded = request.getHeader("X-Forwarded-Host");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        return request.getServerName();
    }
}

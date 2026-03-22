package com.erp.entitybuilder.service.search;

/**
 * Escape special characters for SQL {@code ILIKE} with {@code ESCAPE '!'}.
 * Uses {@code !} instead of backslash so CockroachDB accepts the escape clause
 * (backslash escapes can trigger {@code ilike_escape(): invalid escape string}).
 */
public final class SearchLikeEscape {

    private SearchLikeEscape() {}

    public static String escapeLikePattern(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}

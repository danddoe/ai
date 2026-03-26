package com.erp.entitybuilder.service.ddl;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalizes SQL identifiers to entity/field slugs (lowercase snake_case, length-capped).
 */
public final class DdlSlugUtil {

    private static final Pattern NON_SLUG = Pattern.compile("[^a-z0-9_]+");
    private static final Pattern MULTI_UNDERSCORE = Pattern.compile("_+");

    private DdlSlugUtil() {}

    public static String stripQuotes(String identifier) {
        if (identifier == null) {
            return "";
        }
        String s = identifier.trim();
        if (s.length() >= 2) {
            char a = s.charAt(0);
            char b = s.charAt(s.length() - 1);
            if ((a == '"' && b == '"') || (a == '`' && b == '`') || (a == '[' && b == ']')) {
                s = s.substring(1, s.length() - 1);
            }
        }
        return s;
    }

    /**
     * Table or column name → slug (max 100 chars for entity/field slug limits).
     */
    public static String toSlug(String rawName, int maxLen) {
        String base = stripQuotes(rawName);
        if (base.isEmpty()) {
            return "x";
        }
        String snake = base.replace(' ', '_');
        snake = NON_SLUG.matcher(snake.toLowerCase(Locale.ROOT)).replaceAll("_");
        snake = MULTI_UNDERSCORE.matcher(snake).replaceAll("_");
        snake = snake.replaceAll("^_+|_+$", "");
        if (snake.isEmpty()) {
            snake = "x";
        }
        if (snake.length() > maxLen) {
            snake = snake.substring(0, maxLen).replaceAll("_+$", "");
            if (snake.isEmpty()) {
                snake = "x";
            }
        }
        return snake;
    }

    public static String humanizeSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return "";
        }
        String[] parts = slug.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) {
                sb.append(p.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return sb.toString();
    }
}

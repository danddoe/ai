package com.erp.entitybuilder.domain;

import com.erp.entitybuilder.web.ApiException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.Set;

/**
 * Allowed {@code entities.category_key} values (snake_case), aligned with IAM {@code portal_navigation_items.category_key}.
 * Extend this set when adding product areas.
 */
public final class EntityCategoryKeys {

    /** Keys accepted by the API (lowercase snake_case). */
    public static final Set<String> ALLOWED = Set.of(
            "entity_builder",
            "accounting",
            "accounts_payable",
            "general_ledger",
            "accounts_receivable",
            "inventory",
            "hr",
            "security",
            "lending",
            "master_data"
    );

    private EntityCategoryKeys() {
    }

    /**
     * Trims input; returns null if blank after trim.
     *
     * @throws ApiException {@code bad_request} if non-blank but not in {@link #ALLOWED}
     */
    public static String normalizeOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return null;
        }
        if (!ALLOWED.contains(t)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "bad_request",
                    "categoryKey must be a known module key or omitted",
                    Map.of("categoryKey", raw, "allowed", ALLOWED.stream().sorted().toList())
            );
        }
        return t;
    }
}

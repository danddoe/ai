package com.erp.entitybuilder.service.search;

import com.erp.entitybuilder.domain.EntityField;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Locale;

/**
 * Reads {@code isSearchable} / {@code is_searchable} from {@link EntityField#getConfig()} JSON.
 * PII fields must never contribute to {@code entity_records.search_vector} (enforced in {@code RecordsService}).
 */
public final class FieldSearchability {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FieldSearchability() {}

    public static boolean isSearchable(EntityField field) {
        if (field == null || field.getConfig() == null || field.getConfig().isBlank()) {
            return false;
        }
        try {
            JsonNode n = MAPPER.readTree(field.getConfig());
            Boolean camel = configSearchableFlag(n, "isSearchable");
            if (camel != null) {
                return camel;
            }
            Boolean snake = configSearchableFlag(n, "is_searchable");
            if (snake != null) {
                return snake;
            }
        } catch (Exception ignored) {
            return false;
        }
        return false;
    }

    /**
     * @return {@code null} if the key is absent; otherwise the interpreted flag (booleans, 1/0, "true"/"false", etc.).
     */
    private static Boolean configSearchableFlag(JsonNode root, String key) {
        if (!root.has(key) || root.get(key).isNull()) {
            return null;
        }
        JsonNode v = root.get(key);
        if (v.isBoolean()) {
            return v.asBoolean();
        }
        if (v.isIntegralNumber()) {
            return v.asInt() != 0;
        }
        if (v.isTextual()) {
            String s = v.asText().trim().toLowerCase(Locale.ROOT);
            if ("true".equals(s) || "1".equals(s) || "yes".equals(s)) {
                return true;
            }
            if ("false".equals(s) || "0".equals(s) || "no".equals(s)) {
                return false;
            }
        }
        return false;
    }
}

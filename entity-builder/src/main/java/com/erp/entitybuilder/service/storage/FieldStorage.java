package com.erp.entitybuilder.service.storage;

import com.erp.entitybuilder.domain.EntityField;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Locale;
import java.util.Map;

/**
 * Interprets {@link EntityField#getConfig()} JSON for hybrid domain + EAV entities.
 * See repo doc {@code design/EntityField_storage_contract.md}.
 */
public final class FieldStorage {

    public static final String CONFIG_KEY_STORAGE = "storage";
    public static final String STORAGE_CORE_DOMAIN = "CORE_DOMAIN";
    public static final String STORAGE_EAV_EXTENSION = "EAV_EXTENSION";

    public static final String CONFIG_KEY_CORE_BINDING = "coreBinding";
    public static final String CONFIG_KEY_READ_ONLY = "readOnly";

    private static final ObjectMapper OM = new ObjectMapper();

    private FieldStorage() {}

    /**
     * When {@code true}, field values are owned by a domain service (typed tables), not {@code entity_record_values}.
     * Default is {@code false} (EAV) when {@code storage} is absent or unrecognized.
     */
    public static boolean isCoreDomain(EntityField field) {
        if (field == null) {
            return false;
        }
        String storage = readStorage(field.getConfig());
        return STORAGE_CORE_DOMAIN.equalsIgnoreCase(storage);
    }

    /** Fields whose values persist in {@code entity_record_values} (default). */
    public static boolean isEavExtension(EntityField field) {
        return !isCoreDomain(field);
    }

    /**
     * Reads {@code config.storage}; returns trimmed string or {@code null}.
     */
    public static String readStorage(String configJson) {
        Map<String, Object> m = parseConfig(configJson);
        if (m == null) {
            return null;
        }
        Object v = m.get(CONFIG_KEY_STORAGE);
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    public static Map<String, Object> parseConfig(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return null;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = OM.readValue(configJson, Map.class);
            return m;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    public static String configJson(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return null;
        }
        try {
            return OM.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to serialize field config", e);
        }
    }

    /** Normalize manifest / API input to canonical storage string. */
    public static String normalizeStorageInput(String raw) {
        if (raw == null || raw.isBlank()) {
            return STORAGE_EAV_EXTENSION;
        }
        String u = raw.trim().toUpperCase(Locale.ROOT);
        if (STORAGE_CORE_DOMAIN.equals(u)) {
            return STORAGE_CORE_DOMAIN;
        }
        if (STORAGE_EAV_EXTENSION.equals(u) || "EAV".equals(u)) {
            return STORAGE_EAV_EXTENSION;
        }
        return raw.trim();
    }
}

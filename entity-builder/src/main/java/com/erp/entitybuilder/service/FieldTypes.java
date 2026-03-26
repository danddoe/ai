package com.erp.entitybuilder.service;

import com.erp.entitybuilder.domain.EntityField;

import java.util.Locale;

/**
 * Well-known {@link EntityField#getFieldType()} values beyond primitive EAV types.
 * {@link #DOCUMENT_NUMBER} is stored as {@link com.erp.entitybuilder.domain.EntityRecord#getBusinessDocumentNumber()}.
 * <p>
 * {@code slug=version} with a numeric type is treated as an optimistic-version counter: defaulted on create
 * and incremented on update ({@link com.erp.entitybuilder.service.RecordsService}).
 */
public final class FieldTypes {

    public static final String DOCUMENT_NUMBER = "document_number";

    private FieldTypes() {}

    public static boolean isDocumentNumber(EntityField field) {
        return field != null
                && field.getFieldType() != null
                && DOCUMENT_NUMBER.equalsIgnoreCase(field.getFieldType().trim());
    }

    /**
     * Row / entity version column (e.g. from DDL import). Not the same as API or JSON schema version.
     * <p>
     * Matches slug {@code version} (case-insensitive) and a numeric storage type. If {@code fieldType}
     * is blank (mis-synced metadata), the column is still treated as the row version counter so create
     * can default it and required-field validation does not block saves.
     */
    public static boolean isOptimisticVersionField(EntityField field) {
        if (field == null || field.getSlug() == null) {
            return false;
        }
        if (!isVersionSlug(field.getSlug())) {
            return false;
        }
        if (field.getFieldType() == null || field.getFieldType().isBlank()) {
            return true;
        }
        if (isClearlyNonNumericField(field.getFieldType())) {
            return false;
        }
        return isNumericFieldType(field.getFieldType());
    }

    /** True for slug {@code version} (trimmed, case-insensitive). */
    public static boolean isVersionSlug(String slug) {
        return slug != null && "version".equalsIgnoreCase(slug.trim());
    }

    /**
     * Strip SQL precision/scale e.g. {@code numeric(19,0)} → {@code numeric}, and lowercase.
     */
    public static String normalizeSqlFieldType(String fieldType) {
        if (fieldType == null) {
            return "";
        }
        String t = fieldType.trim().toLowerCase(Locale.ROOT);
        int p = t.indexOf('(');
        if (p >= 0) {
            t = t.substring(0, p).trim();
        }
        return t;
    }

    private static boolean isClearlyNonNumericField(String fieldType) {
        String t = normalizeSqlFieldType(fieldType);
        return switch (t) {
            case "string", "text", "boolean", "date", "datetime", "reference", "document_number" -> true;
            default -> false;
        };
    }

    /**
     * Types stored in {@link com.erp.entitybuilder.domain.EntityRecordValue#getValueNumber()} for EAV.
     */
    public static boolean isNumericFieldType(String fieldType) {
        if (fieldType == null) {
            return false;
        }
        String t = normalizeSqlFieldType(fieldType);
        return switch (t) {
            case "number", "integer", "int", "bigint", "long", "decimal", "float", "double",
                    "numeric", "smallint", "serial", "bigserial", "real", "money" -> true;
            default -> false;
        };
    }
}

package com.erp.cataloggen;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class JpaEntityCatalogSource {

    public record CatalogField(String columnName, String fieldType, boolean required, int sortOrder) {
    }

    private JpaEntityCatalogSource() {
    }

    public static List<CatalogField> extractFields(Class<?> entityClass, CatalogSpec.EntitySpec spec) {
        List<CatalogField> out = new ArrayList<>();
        Set<String> exclude = Set.copyOf(spec.getExcludeColumns());
        int order = 10;
        for (Field field : entityClass.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            if (field.isAnnotationPresent(Transient.class)) {
                continue;
            }
            if (field.isAnnotationPresent(Id.class)) {
                continue;
            }
            Column column = field.getAnnotation(Column.class);
            if (column == null) {
                continue;
            }
            String columnName = resolveColumnName(field, column);
            if (!spec.isIncludeTenantId() && "tenant_id".equals(columnName)) {
                continue;
            }
            if (!spec.isIncludeAuditTimestamps()
                    && ("created_at".equals(columnName) || "updated_at".equals(columnName))) {
                continue;
            }
            if (exclude.contains(columnName)) {
                continue;
            }
            String fieldType = mapFieldType(field.getType());
            boolean required = !column.nullable();
            out.add(new CatalogField(columnName, fieldType, required, order));
            order += 10;
        }
        return out;
    }

    private static String resolveColumnName(Field field, Column column) {
        String name = column.name();
        if (name == null || name.isBlank()) {
            return snakeCase(field.getName());
        }
        return name;
    }

    static String snakeCase(String javaName) {
        if (javaName == null || javaName.isEmpty()) {
            return javaName;
        }
        StringBuilder sb = new StringBuilder();
        char[] chars = javaName.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (i > 0 && Character.isUpperCase(c) && Character.isLowerCase(chars[i - 1])) {
                sb.append('_');
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    private static String mapFieldType(Class<?> t) {
        if (t.isEnum()) {
            return "string";
        }
        if (t == boolean.class || t == Boolean.class) {
            return "boolean";
        }
        if (t == char.class || t == Character.class) {
            return "string";
        }
        if (t.isPrimitive()) {
            return "number";
        }
        if (Number.class.isAssignableFrom(t)
                || t == BigDecimal.class
                || t == BigInteger.class) {
            return "number";
        }
        if (t == UUID.class || t == String.class || CharSequence.class.isAssignableFrom(t)) {
            return "string";
        }
        if (Instant.class.isAssignableFrom(t)
                || LocalDate.class.isAssignableFrom(t)
                || LocalDateTime.class.isAssignableFrom(t)) {
            return "date";
        }
        return "string";
    }

    public static String humanNameForColumn(String column) {
        if (column == null || column.isEmpty()) {
            return column;
        }
        String[] parts = column.split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            String w = parts[i];
            if (w.isEmpty()) {
                continue;
            }
            sb.append(titleCaseWord(w, Locale.ROOT));
        }
        return sb.toString();
    }

    private static String titleCaseWord(String word, Locale locale) {
        if (word.length() == 1) {
            return word.toUpperCase(locale);
        }
        return Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase(locale);
    }
}

package com.erp.entitybuilder.domain;

/**
 * Lifecycle state for {@link EntityField}; persisted in {@code entity_fields.status}.
 */
public final class EntityFieldStatuses {

    public static final String ACTIVE = "ACTIVE";
    public static final String INACTIVE = "INACTIVE";

    private EntityFieldStatuses() {}

    public static boolean isActive(EntityField field) {
        if (field == null) {
            return false;
        }
        String s = field.getStatus();
        return s == null || ACTIVE.equalsIgnoreCase(s);
    }
}

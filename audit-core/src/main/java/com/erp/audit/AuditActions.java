package com.erp.audit;

/**
 * Stable {@code audit_log.action} values for cross-service filtering.
 */
public final class AuditActions {

    public static final String ENTITY_RECORD_CREATE = "data.entity_record.create";
    public static final String ENTITY_RECORD_UPDATE = "data.entity_record.update";
    public static final String ENTITY_RECORD_DELETE = "data.entity_record.delete";
    public static final String ENTITY_RECORD_LINK_ADD = "data.entity_record.links.add";
    public static final String ENTITY_RECORD_LINK_DELETE = "data.entity_record.links.delete";

    /** Stable literals; equivalent to {@link #dataPath} for {@code loan_application}. */
    public static final String LOAN_APPLICATION_CREATE = dataPath("loan_application", "create");
    public static final String LOAN_APPLICATION_PATCH = dataPath("loan_application", "patch");

    /**
     * Generic data audit {@code action}: {@code data.{path}.{verb}}.
     * Examples: path {@code core.company} → {@code data.core.company.create};
     * path {@code loan_application} → {@code data.loan_application.patch}.
     */
    public static String dataPath(String path, String verb) {
        if (path == null || path.isBlank() || verb == null || verb.isBlank()) {
            throw new IllegalArgumentException("path and verb must be non-blank");
        }
        return "data." + path.trim() + "." + verb.trim();
    }

    /**
     * Core master-data namespace: {@code data.core.{resourceKey}.{verb}}.
     * Prefer {@link AuditableResource} with path {@code core.{resourceKey}} where practical.
     */
    public static String dataCore(String resourceKey, String verb) {
        if (resourceKey == null || resourceKey.isBlank() || verb == null || verb.isBlank()) {
            throw new IllegalArgumentException("resourceKey and verb must be non-blank");
        }
        return dataPath("core." + resourceKey.trim(), verb.trim());
    }

    private AuditActions() {}
}

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

    public static final String LOAN_APPLICATION_CREATE = "data.loan_application.create";
    public static final String LOAN_APPLICATION_PATCH = "data.loan_application.patch";

    private AuditActions() {}
}

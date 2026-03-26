package com.erp.entitybuilder.domain;

/**
 * Row visibility for {@link EntityRecord}: platform catalog data vs tenant-owned.
 */
public enum RecordScope {
    STANDARD_RECORD,
    TENANT_RECORD
}

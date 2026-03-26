package com.erp.entitybuilder.domain;

/**
 * Whether a schema object is owned by the platform catalog ({@link #STANDARD_OBJECT})
 * or defined by a customer tenant ({@link #TENANT_OBJECT}).
 */
public enum DefinitionScope {
    STANDARD_OBJECT,
    TENANT_OBJECT
}

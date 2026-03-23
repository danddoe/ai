package com.erp.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares stable audit identity for a persisted domain type (JPA entity or similar).
 * {@link AuditableResourceSupport} derives {@code audit_log.action} as {@code data.{path}.{verb}}
 * and {@code resource_type} by convention (see {@link AuditResourceTypes#fromDataPath}).
 * <p>
 * Dynamic entity-builder records do not use this annotation; they use a fixed
 * {@link AuditResourceTypes#ENTITY_RECORD} and entity-scoped payload context instead.
 */
@Inherited
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AuditableResource {

    /**
     * Path after {@code data.} and before the verb, e.g. {@code core.company}, {@code loan_application}.
     */
    String path();

    /**
     * Optional fixed {@code audit_log.resource_type}; if blank, derived from {@link #path()}.
     */
    String resourceType() default "";
}

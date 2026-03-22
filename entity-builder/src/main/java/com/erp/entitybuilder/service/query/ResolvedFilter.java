package com.erp.entitybuilder.service.query;

import java.util.List;
import java.util.UUID;

/** Validated, type-checked filter tree ready for SQL generation. */
public sealed interface ResolvedFilter permits ResolvedFilter.ResolvedGroup, ResolvedFilter.ResolvedClause {

    record ResolvedGroup(String op, List<ResolvedFilter> children) implements ResolvedFilter {}

    record ResolvedClause(UUID fieldId, ValueKind kind, ClauseOp op, List<Object> bindParams) implements ResolvedFilter {}

    enum ValueKind {
        TEXT,
        NUMBER,
        DATE,
        BOOLEAN,
        REFERENCE
    }

    enum ClauseOp {
        EQ,
        NE,
        GT,
        GTE,
        LT,
        LTE,
        BETWEEN,
        IN,
        CONTAINS,
        STARTS_WITH,
        IS_NULL,
        IS_NOT_NULL
    }
}

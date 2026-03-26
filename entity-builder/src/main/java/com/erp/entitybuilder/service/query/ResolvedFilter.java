package com.erp.entitybuilder.service.query;

import java.util.List;
import java.util.UUID;

/** Validated, type-checked filter tree ready for SQL generation. */
public sealed interface ResolvedFilter permits ResolvedFilter.ResolvedGroup, ResolvedFilter.ResolvedClause, ResolvedFilter.ResolvedMetadataClause {

    record ResolvedGroup(String op, List<ResolvedFilter> children) implements ResolvedFilter {}

    record ResolvedClause(UUID fieldId, ValueKind kind, ClauseOp op, List<Object> bindParams) implements ResolvedFilter {}

    /** Filter on {@code entity_records} columns; use reserved keys {@code record.created_at}, etc. */
    record ResolvedMetadataClause(RecordMetadataField metadataField, ClauseOp op, List<Object> bindParams) implements ResolvedFilter {}

    enum RecordMetadataField {
        CREATED_AT("record.created_at", "er.created_at"),
        UPDATED_AT("record.updated_at", "er.updated_at"),
        CREATED_BY("record.created_by", "er.created_by"),
        UPDATED_BY("record.updated_by", "er.updated_by");

        private final String requestKey;
        private final String sqlColumn;

        RecordMetadataField(String requestKey, String sqlColumn) {
            this.requestKey = requestKey;
            this.sqlColumn = sqlColumn;
        }

        public String requestKey() {
            return requestKey;
        }

        public String sqlColumn() {
            return sqlColumn;
        }

        public static RecordMetadataField fromRequestField(String field) {
            if (field == null) {
                return null;
            }
            String t = field.trim();
            for (RecordMetadataField m : values()) {
                if (m.requestKey.equalsIgnoreCase(t)) {
                    return m;
                }
            }
            return null;
        }
    }

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

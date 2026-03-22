package com.erp.entitybuilder.service.query;

import com.erp.entitybuilder.service.search.SearchLikeEscape;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Appends parameterized SQL for {@link ResolvedFilter} trees onto an existing WHERE fragment
 * (after {@code er.tenant_id = ? AND er.entity_id = ?}).
 */
@Component
public class RecordFilterSqlBuilder {

    public void append(StringBuilder sql, List<Object> params, ResolvedFilter node) {
        if (node instanceof ResolvedFilter.ResolvedGroup g) {
            appendGroup(sql, params, g);
        } else if (node instanceof ResolvedFilter.ResolvedClause c) {
            appendClause(sql, params, c);
        }
    }

    private void appendGroup(StringBuilder sql, List<Object> params, ResolvedFilter.ResolvedGroup g) {
        sql.append('(');
        String joiner = "and".equals(g.op()) ? " AND " : " OR ";
        List<ResolvedFilter> ch = g.children();
        for (int i = 0; i < ch.size(); i++) {
            if (i > 0) {
                sql.append(joiner);
            }
            append(sql, params, ch.get(i));
        }
        sql.append(')');
    }

    private void appendClause(StringBuilder sql, List<Object> params, ResolvedFilter.ResolvedClause c) {
        switch (c.op()) {
            case IS_NULL -> appendIsNull(sql, params, c);
            case IS_NOT_NULL -> appendIsNotNull(sql, params, c);
            default -> appendValuePredicate(sql, params, c);
        }
    }

    private static void appendIsNull(StringBuilder sql, List<Object> params, ResolvedFilter.ResolvedClause c) {
        String col = columnFor(c.kind());
        sql.append("NOT EXISTS (SELECT 1 FROM entity_record_values v WHERE v.record_id = er.id AND v.field_id = ? AND v.")
                .append(col)
                .append(" IS NOT NULL)");
        params.add(c.fieldId());
    }

    private static void appendIsNotNull(StringBuilder sql, List<Object> params, ResolvedFilter.ResolvedClause c) {
        String col = columnFor(c.kind());
        sql.append("EXISTS (SELECT 1 FROM entity_record_values v WHERE v.record_id = er.id AND v.field_id = ? AND v.")
                .append(col)
                .append(" IS NOT NULL)");
        params.add(c.fieldId());
    }

    private void appendValuePredicate(StringBuilder sql, List<Object> params, ResolvedFilter.ResolvedClause c) {
        switch (c.kind()) {
            case TEXT -> appendText(sql, params, c);
            case NUMBER -> appendNumber(sql, params, c);
            case DATE -> appendDate(sql, params, c);
            case BOOLEAN -> appendBoolean(sql, params, c);
            case REFERENCE -> appendReference(sql, params, c);
        }
    }

    private static void appendText(StringBuilder sql, List<Object> params, ResolvedFilter.ResolvedClause c) {
        switch (c.op()) {
            case EQ -> {
                sql.append("EXISTS (SELECT 1 FROM entity_record_values v WHERE v.record_id = er.id AND v.field_id = ? AND v.value_text = ?)");
                params.add(c.fieldId());
                params.add(c.bindParams().get(0));
            }
            case NE -> {
                sql.append("NOT EXISTS (SELECT 1 FROM entity_record_values v WHERE v.record_id = er.id AND v.field_id = ? AND v.value_text = ?)");
                params.add(c.fieldId());
                params.add(c.bindParams().get(0));
            }
            case CONTAINS -> {
                String pat = "%" + SearchLikeEscape.escapeLikePattern(String.valueOf(c.bindParams().get(0))) + "%";
                sql.append("EXISTS (SELECT 1 FROM entity_record_values v WHERE v.record_id = er.id AND v.field_id = ? AND v.value_text ILIKE ? ESCAPE '!')");
                params.add(c.fieldId());
                params.add(pat);
            }
            case STARTS_WITH -> {
                String pat = SearchLikeEscape.escapeLikePattern(String.valueOf(c.bindParams().get(0))) + "%";
                sql.append("EXISTS (SELECT 1 FROM entity_record_values v WHERE v.record_id = er.id AND v.field_id = ? AND v.value_text ILIKE ? ESCAPE '!')");
                params.add(c.fieldId());
                params.add(pat);
            }
            default -> throw new IllegalStateException("text op " + c.op());
        }
    }

    private static void appendNumber(StringBuilder sql, List<Object> params, ResolvedFilter.ResolvedClause c) {
        appendComparable(sql, params, c, "value_number");
    }

    private static void appendDate(StringBuilder sql, List<Object> params, ResolvedFilter.ResolvedClause c) {
        appendComparable(sql, params, c, "value_date");
    }

    private static void appendComparable(StringBuilder sql, List<Object> params, ResolvedFilter.ResolvedClause c, String col) {
        switch (c.op()) {
            case EQ -> {
                sql.append("EXISTS (SELECT 1 FROM entity_record_values v WHERE v.record_id = er.id AND v.field_id = ? AND v.")
                        .append(col).append(" = ?)");
                params.add(c.fieldId());
                params.add(c.bindParams().get(0));
            }
            case NE -> {
                sql.append("NOT EXISTS (SELECT 1 FROM entity_record_values v WHERE v.record_id = er.id AND v.field_id = ? AND v.")
                        .append(col).append(" = ?)");
                params.add(c.fieldId());
                params.add(c.bindParams().get(0));
            }
            case GT -> appendCmp(sql, params, c, col, ">");
            case GTE -> appendCmp(sql, params, c, col, ">=");
            case LT -> appendCmp(sql, params, c, col, "<");
            case LTE -> appendCmp(sql, params, c, col, "<=");
            case BETWEEN -> {
                sql.append("EXISTS (SELECT 1 FROM entity_record_values v WHERE v.record_id = er.id AND v.field_id = ? AND v.")
                        .append(col).append(" >= ? AND v.").append(col).append(" <= ?)");
                params.add(c.fieldId());
                params.add(c.bindParams().get(0));
                params.add(c.bindParams().get(1));
            }
            case IN -> {
                sql.append("EXISTS (SELECT 1 FROM entity_record_values v WHERE v.record_id = er.id AND v.field_id = ? AND v.")
                        .append(col).append(" IN (");
                List<Object> vals = c.bindParams();
                for (int i = 0; i < vals.size(); i++) {
                    if (i > 0) {
                        sql.append(',');
                    }
                    sql.append('?');
                }
                sql.append("))");
                params.add(c.fieldId());
                params.addAll(vals);
            }
            default -> throw new IllegalStateException("comparable op " + c.op());
        }
    }

    private static void appendCmp(StringBuilder sql, List<Object> params, ResolvedFilter.ResolvedClause c, String col, String sym) {
        sql.append("EXISTS (SELECT 1 FROM entity_record_values v WHERE v.record_id = er.id AND v.field_id = ? AND v.")
                .append(col).append(' ').append(sym).append(" ?)");
        params.add(c.fieldId());
        params.add(c.bindParams().get(0));
    }

    private static void appendBoolean(StringBuilder sql, List<Object> params, ResolvedFilter.ResolvedClause c) {
        switch (c.op()) {
            case EQ -> {
                sql.append("EXISTS (SELECT 1 FROM entity_record_values v WHERE v.record_id = er.id AND v.field_id = ? AND v.value_boolean = ?)");
                params.add(c.fieldId());
                params.add(c.bindParams().get(0));
            }
            case NE -> {
                sql.append("NOT EXISTS (SELECT 1 FROM entity_record_values v WHERE v.record_id = er.id AND v.field_id = ? AND v.value_boolean = ?)");
                params.add(c.fieldId());
                params.add(c.bindParams().get(0));
            }
            default -> throw new IllegalStateException("boolean op " + c.op());
        }
    }

    private static void appendReference(StringBuilder sql, List<Object> params, ResolvedFilter.ResolvedClause c) {
        switch (c.op()) {
            case EQ -> {
                sql.append("EXISTS (SELECT 1 FROM entity_record_values v WHERE v.record_id = er.id AND v.field_id = ? AND v.value_reference = ?)");
                params.add(c.fieldId());
                params.add(c.bindParams().get(0));
            }
            case NE -> {
                sql.append("NOT EXISTS (SELECT 1 FROM entity_record_values v WHERE v.record_id = er.id AND v.field_id = ? AND v.value_reference = ?)");
                params.add(c.fieldId());
                params.add(c.bindParams().get(0));
            }
            case IN -> {
                sql.append("EXISTS (SELECT 1 FROM entity_record_values v WHERE v.record_id = er.id AND v.field_id = ? AND v.value_reference IN (");
                List<Object> vals = c.bindParams();
                for (int i = 0; i < vals.size(); i++) {
                    if (i > 0) {
                        sql.append(',');
                    }
                    sql.append('?');
                }
                sql.append("))");
                params.add(c.fieldId());
                params.addAll(vals);
            }
            default -> throw new IllegalStateException("reference op " + c.op());
        }
    }

    private static String columnFor(ResolvedFilter.ValueKind kind) {
        return switch (kind) {
            case TEXT -> "value_text";
            case NUMBER -> "value_number";
            case DATE -> "value_date";
            case BOOLEAN -> "value_boolean";
            case REFERENCE -> "value_reference";
        };
    }
}

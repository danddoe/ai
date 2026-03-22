package com.erp.entitybuilder.service.query;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RecordFilterSqlBuilderTest {

    private final RecordFilterSqlBuilder builder = new RecordFilterSqlBuilder();

    @Test
    void buildsNumberGteExists() {
        UUID fieldId = UUID.randomUUID();
        var clause = new ResolvedFilter.ResolvedClause(
                fieldId,
                ResolvedFilter.ValueKind.NUMBER,
                ResolvedFilter.ClauseOp.GTE,
                List.of(new BigDecimal("100"))
        );
        StringBuilder sql = new StringBuilder();
        List<Object> params = new ArrayList<>();
        builder.append(sql, params, clause);

        assertThat(sql.toString()).contains("entity_record_values");
        assertThat(sql.toString()).contains("value_number");
        assertThat(sql.toString()).contains(">=");
        assertThat(params).containsExactly(fieldId, new BigDecimal("100"));
    }

    @Test
    void buildsAndGroup() {
        UUID f1 = UUID.randomUUID();
        UUID f2 = UUID.randomUUID();
        var c1 = new ResolvedFilter.ResolvedClause(f1, ResolvedFilter.ValueKind.TEXT, ResolvedFilter.ClauseOp.EQ, List.of("a"));
        var c2 = new ResolvedFilter.ResolvedClause(f2, ResolvedFilter.ValueKind.NUMBER, ResolvedFilter.ClauseOp.EQ, List.of(BigDecimal.ONE));
        var group = new ResolvedFilter.ResolvedGroup("and", List.of(c1, c2));

        StringBuilder sql = new StringBuilder();
        List<Object> params = new ArrayList<>();
        builder.append(sql, params, group);

        assertThat(sql.toString()).startsWith("(");
        assertThat(sql.toString()).contains(" AND ");
        assertThat(params).hasSize(4);
    }
}

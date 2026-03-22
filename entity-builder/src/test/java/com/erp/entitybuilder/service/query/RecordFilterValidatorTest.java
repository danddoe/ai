package com.erp.entitybuilder.service.query;

import com.erp.entitybuilder.domain.EntityField;
import com.erp.entitybuilder.web.ApiException;
import com.erp.entitybuilder.web.v1.dto.RecordQueryDtos;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecordFilterValidatorTest {

    private final RecordFilterValidator validator = new RecordFilterValidator();
    private final ObjectMapper om = new ObjectMapper();

    private static EntityField field(UUID id, String slug, String type, boolean pii) {
        EntityField f = new EntityField();
        f.setId(id);
        f.setSlug(slug);
        f.setFieldType(type);
        f.setPii(pii);
        return f;
    }

    @Test
    void nullFilter_returnsNull() {
        assertThat(validator.validate(null, List.of(), true)).isNull();
    }

    @Test
    void numberGte_resolves() throws Exception {
        UUID fid = UUID.randomUUID();
        var clause = new RecordQueryDtos.FilterNode();
        clause.setField("salary");
        clause.setOp("gte");
        clause.setValue(om.readTree("50000"));

        ResolvedFilter r = validator.validate(clause, List.of(field(fid, "salary", "number", false)), true);
        assertThat(r).isInstanceOf(ResolvedFilter.ResolvedClause.class);
        ResolvedFilter.ResolvedClause c = (ResolvedFilter.ResolvedClause) r;
        assertThat(c.fieldId()).isEqualTo(fid);
        assertThat(c.op()).isEqualTo(ResolvedFilter.ClauseOp.GTE);
        assertThat(c.bindParams()).containsExactly(new BigDecimal("50000"));
    }

    @Test
    void piiField_rejectedWithoutPiiRead() {
        UUID fid = UUID.randomUUID();
        var clause = new RecordQueryDtos.FilterNode();
        clause.setField("ssn");
        clause.setOp("eq");
        clause.setValue(om.valueToTree("x"));

        assertThatThrownBy(() -> validator.validate(clause, List.of(field(fid, "ssn", "string", true)), false))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void piiField_allowedWithPiiRead() throws Exception {
        UUID fid = UUID.randomUUID();
        var clause = new RecordQueryDtos.FilterNode();
        clause.setField("ssn");
        clause.setOp("eq");
        clause.setValue(om.valueToTree("abc"));

        ResolvedFilter r = validator.validate(clause, List.of(field(fid, "ssn", "string", true)), true);
        assertThat(r).isInstanceOf(ResolvedFilter.ResolvedClause.class);
    }

    @Test
    void between_parsesTwoInstants() throws Exception {
        UUID fid = UUID.randomUUID();
        JsonNode arr = om.readTree("[\"2024-01-01T00:00:00Z\",\"2024-12-31T00:00:00Z\"]");
        var clause = new RecordQueryDtos.FilterNode();
        clause.setField("hire_date");
        clause.setOp("between");
        clause.setValue(arr);

        ResolvedFilter r = validator.validate(clause, List.of(field(fid, "hire_date", "date", false)), true);
        ResolvedFilter.ResolvedClause c = (ResolvedFilter.ResolvedClause) r;
        assertThat(c.op()).isEqualTo(ResolvedFilter.ClauseOp.BETWEEN);
        assertThat(c.bindParams()).hasSize(2);
    }

    @Test
    void emptyGroup_throws() {
        var g = new RecordQueryDtos.FilterNode();
        g.setOp("and");
        g.setChildren(List.of());

        assertThatThrownBy(() -> validator.validate(g, List.of(), true))
                .isInstanceOf(ApiException.class);
    }
}

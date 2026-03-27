package com.erp.entitybuilder.service.rules;

import com.erp.entitybuilder.web.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleConditionEvaluatorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final RuleConditionEvaluator evaluator = new RuleConditionEvaluator(mapper);

    @Test
    void golden_and_country_us_amount_passes() throws Exception {
        String json = readClasspath("business_rules_golden/condition_and_country_us_amount_gte.json");
        Map<String, Object> values = Map.of(
                "country", "US",
                "amount", new BigDecimal("1500")
        );
        assertThat(evaluator.evaluate(json, values)).isTrue();
    }

    @Test
    void golden_and_country_us_amount_fails_on_amount() throws Exception {
        String json = readClasspath("business_rules_golden/condition_and_country_us_amount_gte.json");
        Map<String, Object> values = Map.of(
                "country", "US",
                "amount", new BigDecimal("100")
        );
        assertThat(evaluator.evaluate(json, values)).isFalse();
    }

    @Test
    void golden_isEmpty_note() throws Exception {
        String json = readClasspath("business_rules_golden/condition_isempty_note.json");
        assertThat(evaluator.evaluate(json, Map.of())).isTrue();
        assertThat(evaluator.evaluate(json, Map.of("note", ""))).isTrue();
        assertThat(evaluator.evaluate(json, Map.of("note", "x"))).isFalse();
    }

    @Test
    void cmp_eq_reference_uuid_coerces() {
        UUID id = UUID.randomUUID();
        String cond = """
                {"op":"cmp","field":"ref","operator":"eq","value":"%s"}
                """.formatted(id);
        assertThat(evaluator.evaluate(cond, Map.of("ref", id.toString()))).isTrue();
        assertThat(evaluator.evaluate(cond, Map.of("ref", id))).isTrue();
    }

    @Test
    void unsupported_op_throws() {
        assertThatThrownBy(() -> evaluator.evaluate("{\"op\":\"xor\",\"children\":[]}", Map.of()))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void or_short_circuits_true() {
        String cond = """
                {"op":"or","children":[
                  {"op":"cmp","field":"a","operator":"eq","value":1},
                  {"op":"cmp","field":"b","operator":"eq","value":2}
                ]}
                """;
        assertThat(evaluator.evaluate(cond, Map.of("a", 1))).isTrue();
    }

    private static String readClasspath(String path) throws Exception {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

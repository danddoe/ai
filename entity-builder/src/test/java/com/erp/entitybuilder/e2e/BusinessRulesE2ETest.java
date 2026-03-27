package com.erp.entitybuilder.e2e;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessRulesE2ETest extends AbstractEntityBuilderE2ETest {

    private static final List<String> PERMS = List.of(
            "entity_builder:schema:read",
            "entity_builder:schema:write",
            "entity_builder:records:read",
            "entity_builder:records:write"
    );

    @Test
    void endToEnd_businessRulesCrud_and_serverRuleBlocksCreate() {
        Assumptions.assumeTrue(jdbcTemplate != null, "E2E skipped: no CockroachDB connection");

        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        var headers = authHeaders(userId, tenantId, PERMS);

        Map<String, Object> createEntity = new LinkedHashMap<>();
        createEntity.put("name", "RuleTest");
        createEntity.put("slug", "ruletest");
        createEntity.put("status", "ACTIVE");
        ResponseEntity<Map> entityResp = restTemplate.exchange(
                baseUrl + "/v1/entities",
                HttpMethod.POST,
                new HttpEntity<>(createEntity, headers),
                Map.class
        );
        assertThat(entityResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String entityId = String.valueOf(entityResp.getBody().get("id"));

        for (var spec : List.of(
                Map.of("name", "Country", "slug", "country", "fieldType", "string", "required", false),
                Map.of("name", "Tax id", "slug", "tax_id", "fieldType", "string", "required", false)
        )) {
            ResponseEntity<Map> fr = restTemplate.exchange(
                    baseUrl + "/v1/entities/" + entityId + "/fields",
                    HttpMethod.POST,
                    new HttpEntity<>(spec, headers),
                    Map.class
            );
            assertThat(fr.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        Map<String, Object> condition = Map.of(
                "op", "cmp",
                "field", "country",
                "operator", "eq",
                "value", "US"
        );
        Map<String, Object> payload = Map.of("field", "tax_id", "message", "Tax id required for US");
        Map<String, Object> action = Map.of(
                "priority", 0,
                "actionType", "SERVER_ADD_ERROR",
                "payload", payload,
                "applyUi", false,
                "applyServer", true
        );
        Map<String, Object> createRule = new LinkedHashMap<>();
        createRule.put("name", "us_tax_required");
        createRule.put("description", "demo");
        createRule.put("priority", 0);
        createRule.put("trigger", "BEFORE_CREATE");
        createRule.put("condition", condition);
        createRule.put("formLayoutId", null);
        createRule.put("active", true);
        createRule.put("actions", List.of(action));

        ResponseEntity<Map> ruleResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + entityId + "/business-rules",
                HttpMethod.POST,
                new HttpEntity<>(createRule, headers),
                Map.class
        );
        assertThat(ruleResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> createdRule = ruleResp.getBody();
        assertThat(createdRule).isNotNull();
        String ruleId = String.valueOf(createdRule.get("id"));

        ResponseEntity<List> listResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + entityId + "/business-rules?surface=SERVER",
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                List.class
        );
        assertThat(listResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResp.getBody()).hasSize(1);

        Map<String, Object> badValues = new LinkedHashMap<>();
        badValues.put("name", "x");
        badValues.put("country", "US");
        ResponseEntity<Map> badCreate = restTemplate.exchange(
                baseUrl + "/v1/tenants/" + tenantId + "/entities/" + entityId + "/records",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("values", badValues), headers),
                Map.class
        );
        assertThat(badCreate.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        Map<String, Object> goodValues = new LinkedHashMap<>();
        goodValues.put("name", "y");
        goodValues.put("country", "US");
        goodValues.put("tax_id", "T-1");
        ResponseEntity<Map> goodCreate = restTemplate.exchange(
                baseUrl + "/v1/tenants/" + tenantId + "/entities/" + entityId + "/records",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("values", goodValues), headers),
                Map.class
        );
        assertThat(goodCreate.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Void> delResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + entityId + "/business-rules/" + ruleId,
                HttpMethod.DELETE,
                new HttpEntity<>(null, headers),
                Void.class
        );
        assertThat(delResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<List> listAfter = restTemplate.exchange(
                baseUrl + "/v1/entities/" + entityId + "/business-rules?surface=SERVER",
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                List.class
        );
        assertThat(listAfter.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listAfter.getBody()).isEmpty();
    }
}

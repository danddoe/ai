package com.erp.entitybuilder.e2e;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FormLayoutTemplatesE2ETest extends AbstractEntityBuilderE2ETest {

    private static final List<String> SCHEMA_PERMS = List.of(
            "entity_builder:schema:read",
            "entity_builder:schema:write"
    );

    @Test
    void endToEnd_listTemplates_andCreateFromTemplate_mapsFieldSlug() {
        Assumptions.assumeTrue(jdbcTemplate != null, "E2E skipped: no CockroachDB connection");

        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        var headers = authHeaders(userId, tenantId, SCHEMA_PERMS);

        ResponseEntity<List> templatesResp = restTemplate.exchange(
                baseUrl + "/v1/form-layout-templates",
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                List.class
        );
        assertThat(templatesResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(templatesResp.getBody()).isNotEmpty();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> templates = (List<Map<String, Object>>) templatesResp.getBody();
        assertThat(templates.stream().map(m -> (String) m.get("templateKey"))).contains("blank-v2", "simple-order");

        Map<String, Object> createEntity = Map.of("name", "Sales Order", "slug", "sales_order", "status", "ACTIVE");
        ResponseEntity<Map> entityResp = restTemplate.exchange(
                baseUrl + "/v1/entities",
                HttpMethod.POST,
                new HttpEntity<>(createEntity, headers),
                Map.class
        );
        assertThat(entityResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String entityId = String.valueOf(entityResp.getBody().get("id"));

        Map<String, Object> createField = Map.of(
                "name", "Order number",
                "slug", "order_number",
                "fieldType", "text",
                "required", false,
                "pii", false
        );
        ResponseEntity<Map> fieldResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + entityId + "/fields",
                HttpMethod.POST,
                new HttpEntity<>(createField, headers),
                Map.class
        );
        assertThat(fieldResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String fieldId = String.valueOf(fieldResp.getBody().get("id"));

        Map<String, Object> fromTpl = Map.of(
                "templateKey", "simple-order",
                "name", "From template",
                "isDefault", true
        );
        ResponseEntity<Map> layoutResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + entityId + "/form-layouts/from-template",
                HttpMethod.POST,
                new HttpEntity<>(fromTpl, headers),
                Map.class
        );
        assertThat(layoutResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(layoutResp.getBody().get("name")).isEqualTo("From template");

        @SuppressWarnings("unchecked")
        Map<String, Object> layout = (Map<String, Object>) layoutResp.getBody().get("layout");
        assertThat(layout.get("version")).isEqualTo(2);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> regions = (List<Map<String, Object>>) layout.get("regions");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) regions.get(0).get("rows");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> columns = (List<Map<String, Object>>) rows.get(0).get("columns");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) columns.get(0).get("items");
        assertThat(String.valueOf(items.get(0).get("fieldId"))).isEqualTo(fieldId);
        assertThat(items.get(0).get("fieldSlug")).isEqualTo("order_number");
    }
}

package com.erp.entitybuilder.e2e;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RecordListViewsE2ETest extends AbstractEntityBuilderE2ETest {

    private static final List<String> SCHEMA_PERMS = List.of(
            "entity_builder:schema:read",
            "entity_builder:schema:write"
    );

    @Test
    void endToEnd_recordListViewsCrud() {
        Assumptions.assumeTrue(jdbcTemplate != null, "E2E skipped: no CockroachDB connection");

        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        var headers = authHeaders(userId, tenantId, SCHEMA_PERMS);

        Map<String, Object> createEntity = Map.of("name", "Order", "slug", "order_rlv", "status", "ACTIVE");
        ResponseEntity<Map> entityResp = restTemplate.exchange(
                baseUrl + "/v1/entities",
                HttpMethod.POST,
                new HttpEntity<>(createEntity, headers),
                Map.class
        );
        assertThat(entityResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String entityId = String.valueOf(entityResp.getBody().get("id"));

        Map<String, Object> createField = Map.of(
                "name", "Amount",
                "slug", "amount",
                "fieldType", "number",
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

        Map<String, Object> col0 = new LinkedHashMap<>();
        col0.put("fieldSlug", "amount");
        col0.put("order", 0);
        col0.put("align", "right");
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("version", 1);
        definition.put("columns", List.of(col0));
        definition.put("showRowActions", true);

        Map<String, Object> create = Map.of(
                "name", "Default list",
                "definition", definition,
                "isDefault", true
        );
        ResponseEntity<Map> createResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + entityId + "/record-list-views",
                HttpMethod.POST,
                new HttpEntity<>(create, headers),
                Map.class
        );
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String viewId = String.valueOf(createResp.getBody().get("id"));
        assertThat(createResp.getBody().get("name")).isEqualTo("Default list");
        assertThat(createResp.getBody().get("isDefault")).isEqualTo(true);

        ResponseEntity<List> listResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + entityId + "/record-list-views",
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                List.class
        );
        assertThat(listResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResp.getBody()).hasSize(1);

        ResponseEntity<Map> getResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + entityId + "/record-list-views/" + viewId,
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                Map.class
        );
        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResp.getBody().get("definition")).isNotNull();

        Map<String, Object> updateDef = new LinkedHashMap<>();
        updateDef.put("version", 1);
        updateDef.put("columns", List.of(
                Map.of("fieldSlug", "amount", "order", 0, "label", "Total", "inlineEditable", true)
        ));
        updateDef.put("showRowActions", false);
        Map<String, Object> update = Map.of("name", "Renamed list", "definition", updateDef);
        ResponseEntity<Map> updateResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + entityId + "/record-list-views/" + viewId,
                HttpMethod.PATCH,
                new HttpEntity<>(update, headers),
                Map.class
        );
        assertThat(updateResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResp.getBody().get("name")).isEqualTo("Renamed list");

        ResponseEntity<Void> deleteResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + entityId + "/record-list-views/" + viewId,
                HttpMethod.DELETE,
                new HttpEntity<>(null, headers),
                Void.class
        );
        assertThat(deleteResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> getAfterResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + entityId + "/record-list-views/" + viewId,
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                Map.class
        );
        assertThat(getAfterResp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createListView_withWipStatus_persists() {
        Assumptions.assumeTrue(jdbcTemplate != null, "E2E skipped: no CockroachDB connection");

        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        var headers = authHeaders(userId, tenantId, SCHEMA_PERMS);

        Map<String, Object> createEntity = Map.of("name", "WIP Order", "slug", "order_wip_rlv", "status", "ACTIVE");
        ResponseEntity<Map> entityResp = restTemplate.exchange(
                baseUrl + "/v1/entities",
                HttpMethod.POST,
                new HttpEntity<>(createEntity, headers),
                Map.class
        );
        assertThat(entityResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String entityId = String.valueOf(entityResp.getBody().get("id"));

        Map<String, Object> createField = Map.of(
                "name", "Note",
                "slug", "note",
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

        Map<String, Object> col0 = new LinkedHashMap<>();
        col0.put("fieldSlug", "note");
        col0.put("order", 0);
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("version", 1);
        definition.put("columns", List.of(col0));
        definition.put("showRowActions", true);

        Map<String, Object> create = new LinkedHashMap<>();
        create.put("name", "Draft list");
        create.put("definition", definition);
        create.put("isDefault", false);
        create.put("status", "WIP");

        ResponseEntity<Map> createResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + entityId + "/record-list-views",
                HttpMethod.POST,
                new HttpEntity<>(create, headers),
                Map.class
        );
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(createResp.getBody().get("status")).isEqualTo("WIP");
    }
}

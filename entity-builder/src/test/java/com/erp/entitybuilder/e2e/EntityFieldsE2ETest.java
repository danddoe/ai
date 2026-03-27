package com.erp.entitybuilder.e2e;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EntityFieldsE2ETest extends AbstractEntityBuilderE2ETest {

    private static final List<String> SCHEMA_PERMS = List.of(
            "entity_builder:schema:read",
            "entity_builder:schema:write"
    );

    @Test
    void endToEnd_entityFieldsCrud() {
        Assumptions.assumeTrue(jdbcTemplate != null, "E2E skipped: no CockroachDB connection");

        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        var headers = authHeaders(userId, tenantId, SCHEMA_PERMS);

        // Create entity first
        Map<String, Object> createEntity = Map.of("name", "Product", "slug", "product", "status", "ACTIVE");
        ResponseEntity<Map> entityResp = restTemplate.exchange(
                baseUrl + "/v1/entities",
                HttpMethod.POST,
                new HttpEntity<>(createEntity, headers),
                Map.class
        );
        assertThat(entityResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String entityId = String.valueOf(entityResp.getBody().get("id"));

        // Create field
        Map<String, Object> createField = Map.of(
                "name", "Price",
                "slug", "price",
                "fieldType", "number",
                "required", true,
                "pii", false
        );
        ResponseEntity<Map> createResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + entityId + "/fields",
                HttpMethod.POST,
                new HttpEntity<>(createField, headers),
                Map.class
        );
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String fieldId = String.valueOf(createResp.getBody().get("id"));
        assertThat(createResp.getBody().get("name")).isEqualTo("Price");
        assertThat(createResp.getBody().get("fieldType")).isEqualTo("number");
        assertThat(createResp.getBody().get("definitionScope")).isEqualTo("TENANT_OBJECT");

        // List fields
        ResponseEntity<List> listResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + entityId + "/fields",
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                List.class
        );
        assertThat(listResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResp.getBody()).hasSize(2);
        @SuppressWarnings("unchecked")
        List<Map<?, ?>> fieldRows = (List<Map<?, ?>>) (List<?>) listResp.getBody();
        List<String> slugs = fieldRows.stream().map(m -> String.valueOf(m.get("slug"))).toList();
        assertThat(slugs).containsExactlyInAnyOrder("name", "price");

        // Get field
        ResponseEntity<Map> getResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + entityId + "/fields/" + fieldId,
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                Map.class
        );
        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResp.getBody().get("slug")).isEqualTo("price");
        assertThat(getResp.getBody().get("definitionScope")).isEqualTo("TENANT_OBJECT");

        // Update field (display format for clients, e.g. number pattern)
        Map<String, Object> update = new LinkedHashMap<>();
        update.put("name", "Unit Price");
        update.put("required", false);
        update.put("formatString", "#,##0.00");
        ResponseEntity<Map> updateResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + entityId + "/fields/" + fieldId,
                HttpMethod.PATCH,
                new HttpEntity<>(update, headers),
                Map.class
        );
        assertThat(updateResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResp.getBody().get("name")).isEqualTo("Unit Price");
        assertThat(updateResp.getBody().get("formatString")).isEqualTo("#,##0.00");

        ResponseEntity<Map> clearFormatResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + entityId + "/fields/" + fieldId,
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("formatString", ""), headers),
                Map.class
        );
        assertThat(clearFormatResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(clearFormatResp.getBody().get("formatString")).isNull();

        // Delete field
        ResponseEntity<Map> deleteResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + entityId + "/fields/" + fieldId,
                HttpMethod.DELETE,
                new HttpEntity<>(null, headers),
                Map.class
        );
        assertThat(deleteResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(deleteResp.getBody().get("outcome")).isEqualTo("DELETED");

        // Verify deleted
        ResponseEntity<Map> getAfterResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + entityId + "/fields/" + fieldId,
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                Map.class
        );
        assertThat(getAfterResp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void endToEnd_entityFieldsSortOrderAndLabelOverride() {
        Assumptions.assumeTrue(jdbcTemplate != null, "E2E skipped: no CockroachDB connection");

        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        var headers = authHeaders(userId, tenantId, SCHEMA_PERMS);

        Map<String, Object> createEntity = Map.of("name", "SortTest", "slug", "sort_test_entity", "status", "ACTIVE");
        ResponseEntity<Map> entityResp = restTemplate.exchange(
                baseUrl + "/v1/entities",
                HttpMethod.POST,
                new HttpEntity<>(createEntity, headers),
                Map.class
        );
        assertThat(entityResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String entityId = String.valueOf(entityResp.getBody().get("id"));

        Map<String, Object> createAlpha = new LinkedHashMap<>();
        createAlpha.put("name", "Alpha");
        createAlpha.put("slug", "alpha");
        createAlpha.put("fieldType", "string");
        createAlpha.put("required", false);
        createAlpha.put("pii", false);
        createAlpha.put("sortOrder", 10);

        Map<String, Object> createBeta = new LinkedHashMap<>();
        createBeta.put("name", "Beta");
        createBeta.put("slug", "beta");
        createBeta.put("fieldType", "string");
        createBeta.put("required", false);
        createBeta.put("pii", false);
        createBeta.put("sortOrder", 0);

        ResponseEntity<Map> alphaResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + entityId + "/fields",
                HttpMethod.POST,
                new HttpEntity<>(createAlpha, headers),
                Map.class
        );
        assertThat(alphaResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String alphaId = String.valueOf(alphaResp.getBody().get("id"));

        ResponseEntity<Map> betaResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + entityId + "/fields",
                HttpMethod.POST,
                new HttpEntity<>(createBeta, headers),
                Map.class
        );
        assertThat(betaResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<List> listResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + entityId + "/fields",
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                List.class
        );
        assertThat(listResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResp.getBody()).hasSize(3);
        @SuppressWarnings("unchecked")
        List<Map<?, ?>> rows = (List<Map<?, ?>>) (List<?>) listResp.getBody();
        assertThat(rows.get(0).get("slug")).isEqualTo("beta");
        assertThat(rows.get(1).get("slug")).isEqualTo("name");
        assertThat(rows.get(2).get("slug")).isEqualTo("alpha");

        Map<String, Object> patchLabel = Map.of("labelOverride", "Alpha display");
        ResponseEntity<Map> patchResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + entityId + "/fields/" + alphaId,
                HttpMethod.PATCH,
                new HttpEntity<>(patchLabel, headers),
                Map.class
        );
        assertThat(patchResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(patchResp.getBody().get("labelOverride")).isEqualTo("Alpha display");
        assertThat(patchResp.getBody().get("displayLabel")).isEqualTo("Alpha display");

        ResponseEntity<Map> getResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + entityId + "/fields/" + alphaId,
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                Map.class
        );
        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResp.getBody().get("labelOverride")).isEqualTo("Alpha display");
        assertThat(getResp.getBody().get("displayLabel")).isEqualTo("Alpha display");

        Map<String, Object> clearLabel = Map.of("labelOverride", "");
        ResponseEntity<Map> clearResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + entityId + "/fields/" + alphaId,
                HttpMethod.PATCH,
                new HttpEntity<>(clearLabel, headers),
                Map.class
        );
        assertThat(clearResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(clearResp.getBody().get("labelOverride")).isNull();
        assertThat(clearResp.getBody().get("displayLabel")).isEqualTo("Alpha");
    }

    @Test
    void endToEnd_fieldLabels_putLocale_respectsAcceptLanguage() {
        Assumptions.assumeTrue(jdbcTemplate != null, "E2E skipped: no CockroachDB connection");

        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        var headers = authHeaders(userId, tenantId, SCHEMA_PERMS);

        Map<String, Object> createEntity = Map.of("name", "Sku", "slug", "sku_i18n", "status", "ACTIVE");
        ResponseEntity<Map> entityResp = restTemplate.exchange(
                baseUrl + "/v1/entities",
                HttpMethod.POST,
                new HttpEntity<>(createEntity, headers),
                Map.class
        );
        assertThat(entityResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String entityId = String.valueOf(entityResp.getBody().get("id"));

        Map<String, Object> createField = Map.of(
                "name", "List price",
                "slug", "list_price",
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
        String fieldId = String.valueOf(fieldResp.getBody().get("id"));

        HttpHeaders headersEs = authHeaders(userId, tenantId, SCHEMA_PERMS);
        headersEs.set("Accept-Language", "es");
        ResponseEntity<Map> putEs = restTemplate.exchange(
                baseUrl + "/v1/entities/" + entityId + "/fields/" + fieldId + "/labels/es",
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("label", "Precio lista"), headersEs),
                Map.class
        );
        assertThat(putEs.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(putEs.getBody().get("displayLabel")).isEqualTo("Precio lista");
        @SuppressWarnings("unchecked")
        Map<String, Object> labels = (Map<String, Object>) putEs.getBody().get("labels");
        assertThat(labels.get("es")).isEqualTo("Precio lista");

        HttpHeaders headersEn = authHeaders(userId, tenantId, SCHEMA_PERMS);
        headersEn.set("Accept-Language", "en");
        ResponseEntity<Map> getEn = restTemplate.exchange(
                baseUrl + "/v1/entities/" + entityId + "/fields/" + fieldId,
                HttpMethod.GET,
                new HttpEntity<>(null, headersEn),
                Map.class
        );
        assertThat(getEn.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getEn.getBody().get("displayLabel")).isEqualTo("List price");
    }

    private static final List<String> SCHEMA_AND_RECORDS_PERMS = List.of(
            "entity_builder:schema:read",
            "entity_builder:schema:write",
            "entity_builder:records:read",
            "entity_builder:records:write"
    );

    @Test
    void endToEnd_deleteField_deactivatesWhenRecordHoldsValue() {
        Assumptions.assumeTrue(jdbcTemplate != null, "E2E skipped: no CockroachDB connection");

        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        var headers = authHeaders(userId, tenantId, SCHEMA_AND_RECORDS_PERMS);

        Map<String, Object> createEntity = Map.of("name", "Widget", "slug", "widget_del", "status", "ACTIVE");
        ResponseEntity<Map> entityResp = restTemplate.exchange(
                baseUrl + "/v1/entities",
                HttpMethod.POST,
                new HttpEntity<>(createEntity, headers),
                Map.class
        );
        assertThat(entityResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String entityId = String.valueOf(entityResp.getBody().get("id"));

        Map<String, Object> createField = new LinkedHashMap<>();
        createField.put("name", "SKU");
        createField.put("slug", "sku");
        createField.put("fieldType", "string");
        createField.put("required", false);
        createField.put("pii", false);
        ResponseEntity<Map> createFieldResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + entityId + "/fields",
                HttpMethod.POST,
                new HttpEntity<>(createField, headers),
                Map.class
        );
        assertThat(createFieldResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String fieldId = String.valueOf(createFieldResp.getBody().get("id"));

        Map<String, Object> createRecord = Map.of("values", Map.of("name", "x", "sku", "ABC-1"));
        ResponseEntity<Map> recordResp = restTemplate.exchange(
                baseUrl + "/v1/tenants/" + tenantId + "/entities/" + entityId + "/records",
                HttpMethod.POST,
                new HttpEntity<>(createRecord, headers),
                Map.class
        );
        assertThat(recordResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map> deleteResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + entityId + "/fields/" + fieldId,
                HttpMethod.DELETE,
                new HttpEntity<>(null, headers),
                Map.class
        );
        assertThat(deleteResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(deleteResp.getBody().get("outcome")).isEqualTo("DEACTIVATED");

        ResponseEntity<Map> getFieldResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + entityId + "/fields/" + fieldId,
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                Map.class
        );
        assertThat(getFieldResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getFieldResp.getBody().get("status")).isEqualTo("INACTIVE");
        assertThat(getFieldResp.getBody().get("required")).isEqualTo(false);
    }
}

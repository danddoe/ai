package com.erp.entitybuilder.e2e;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RecordsE2ETest extends AbstractEntityBuilderE2ETest {

    private static final List<String> RECORDS_PERMS = List.of(
            "entity_builder:schema:read",
            "entity_builder:schema:write",
            "entity_builder:records:read",
            "entity_builder:records:write"
    );

    @Test
    void endToEnd_recordsCrud() {
        Assumptions.assumeTrue(jdbcTemplate != null, "E2E skipped: no CockroachDB connection");

        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        var headers = authHeaders(userId, tenantId, RECORDS_PERMS);

        // Create entity with field
        Map<String, Object> createEntity = Map.of("name", "Task", "slug", "task", "status", "ACTIVE");
        ResponseEntity<Map> entityResp = restTemplate.exchange(
                baseUrl + "/v1/entities",
                HttpMethod.POST,
                new HttpEntity<>(createEntity, headers),
                Map.class
        );
        assertThat(entityResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String entityId = String.valueOf(entityResp.getBody().get("id"));

        Map<String, Object> createField = Map.of(
                "name", "Title",
                "slug", "title",
                "fieldType", "string",
                "required", true
        );
        restTemplate.exchange(
                baseUrl + "/v1/entities/" + entityId + "/fields",
                HttpMethod.POST,
                new HttpEntity<>(createField, headers),
                Map.class
        );

        // Create record
        Map<String, Object> values = Map.of("title", "First task");
        Map<String, Object> create = Map.of("values", values);
        ResponseEntity<Map> createResp = restTemplate.exchange(
                baseUrl + "/v1/tenants/" + tenantId + "/entities/" + entityId + "/records",
                HttpMethod.POST,
                new HttpEntity<>(create, headers),
                Map.class
        );
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String recordId = String.valueOf(createResp.getBody().get("id"));
        assertThat(createResp.getBody().get("values")).isNotNull();

        // List records
        ResponseEntity<Map> listResp = restTemplate.exchange(
                baseUrl + "/v1/tenants/" + tenantId + "/entities/" + entityId + "/records?page=1&pageSize=10",
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                Map.class
        );
        assertThat(listResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> items = (List<?>) listResp.getBody().get("items");
        assertThat(items).hasSize(1);

        // Get record
        ResponseEntity<Map> getResp = restTemplate.exchange(
                baseUrl + "/v1/tenants/" + tenantId + "/entities/" + entityId + "/records/" + recordId,
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                Map.class
        );
        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResp.getBody().get("id")).isEqualTo(recordId);

        // Update record
        Map<String, Object> updatedValues = Map.of("title", "First task - updated");
        Map<String, Object> update = Map.of("values", updatedValues);
        ResponseEntity<Map> updateResp = restTemplate.exchange(
                baseUrl + "/v1/tenants/" + tenantId + "/entities/" + entityId + "/records/" + recordId,
                HttpMethod.PATCH,
                new HttpEntity<>(update, headers),
                Map.class
        );
        assertThat(updateResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResp.getBody().get("values")).isNotNull();

        // Delete record
        ResponseEntity<Void> deleteResp = restTemplate.exchange(
                baseUrl + "/v1/tenants/" + tenantId + "/entities/" + entityId + "/records/" + recordId,
                HttpMethod.DELETE,
                new HttpEntity<>(null, headers),
                Void.class
        );
        assertThat(deleteResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Verify deleted
        ResponseEntity<Map> getAfterResp = restTemplate.exchange(
                baseUrl + "/v1/tenants/" + tenantId + "/entities/" + entityId + "/records/" + recordId,
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                Map.class
        );
        assertThat(getAfterResp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}

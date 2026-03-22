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

        UUID rid = UUID.fromString(recordId);
        Integer auditAfterCreate = jdbcTemplate.queryForObject(
                "SELECT count(*)::int FROM audit_log WHERE tenant_id = ? AND resource_id = ? AND source_service = ?",
                Integer.class, tenantId, rid, "entity-builder");
        assertThat(auditAfterCreate).isEqualTo(1);

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

        Integer auditAfterUpdate = jdbcTemplate.queryForObject(
                "SELECT count(*)::int FROM audit_log WHERE tenant_id = ? AND resource_id = ? AND source_service = ?",
                Integer.class, tenantId, rid, "entity-builder");
        assertThat(auditAfterUpdate).isEqualTo(2);

        String recordAuditUrl = baseUrl + "/v1/tenants/" + tenantId + "/entities/" + entityId + "/records/" + recordId + "/audit-events?page=1&pageSize=10";
        ResponseEntity<Map> auditListResp = restTemplate.exchange(
                recordAuditUrl,
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                Map.class
        );
        assertThat(auditListResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> auditItems = (List<Map<String, Object>>) auditListResp.getBody().get("items");
        assertThat(auditItems).hasSize(2);
        assertThat(auditListResp.getBody().get("total")).isEqualTo(2);

        String entityAuditUrl = baseUrl + "/v1/tenants/" + tenantId + "/entities/" + entityId + "/audit-events?page=1&pageSize=50";
        ResponseEntity<Map> entityAuditResp = restTemplate.exchange(
                entityAuditUrl,
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                Map.class
        );
        assertThat(entityAuditResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entityAuditItems = (List<Map<String, Object>>) entityAuditResp.getBody().get("items");
        assertThat(entityAuditItems.size()).isGreaterThanOrEqualTo(2);

        UUID wrongEntityId = UUID.randomUUID();
        ResponseEntity<Map> wrongEntityAudit = restTemplate.exchange(
                baseUrl + "/v1/tenants/" + tenantId + "/entities/" + wrongEntityId + "/records/" + recordId + "/audit-events",
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                Map.class
        );
        assertThat(wrongEntityAudit.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Delete record
        ResponseEntity<Void> deleteResp = restTemplate.exchange(
                baseUrl + "/v1/tenants/" + tenantId + "/entities/" + entityId + "/records/" + recordId,
                HttpMethod.DELETE,
                new HttpEntity<>(null, headers),
                Void.class
        );
        assertThat(deleteResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        Integer auditAfterDelete = jdbcTemplate.queryForObject(
                "SELECT count(*)::int FROM audit_log WHERE tenant_id = ? AND resource_id = ? AND source_service = ?",
                Integer.class, tenantId, rid, "entity-builder");
        assertThat(auditAfterDelete).isEqualTo(3);

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

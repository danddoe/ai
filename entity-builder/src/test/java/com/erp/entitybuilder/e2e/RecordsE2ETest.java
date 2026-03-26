package com.erp.entitybuilder.e2e;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
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
        Map<String, Object> values = Map.of("name", "First task", "title", "First task");
        Map<String, Object> create = Map.of("values", values);
        ResponseEntity<Map> createResp = restTemplate.exchange(
                baseUrl + "/v1/tenants/" + tenantId + "/entities/" + entityId + "/records",
                HttpMethod.POST,
                new HttpEntity<>(create, headers),
                Map.class
        );
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("unchecked")
        Map<String, Object> createdBody = (Map<String, Object>) createResp.getBody();
        String recordId = String.valueOf(createdBody.get("id"));
        assertThat(createdBody.get("values")).isNotNull();
        assertRecordMetadataPresentAfterCreate(createdBody, userId);

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
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> listItems =
                (List<Map<String, Object>>) (List<?>) listResp.getBody().get("items");
        assertThat(listItems).hasSize(1);
        assertRecordMetadataPresentAfterCreate(listItems.get(0), userId);

        // Get record
        ResponseEntity<Map> getResp = restTemplate.exchange(
                baseUrl + "/v1/tenants/" + tenantId + "/entities/" + entityId + "/records/" + recordId,
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                Map.class
        );
        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> gotBody = (Map<String, Object>) getResp.getBody();
        assertThat(gotBody.get("id")).isEqualTo(recordId);
        assertRecordMetadataPresentAfterCreate(gotBody, userId);

        // Structured query: filter by record.created_by (reserved metadata field)
        Map<String, Object> byActorClause = new LinkedHashMap<>();
        byActorClause.put("field", "record.created_by");
        byActorClause.put("op", "eq");
        byActorClause.put("value", userId.toString());
        Map<String, Object> queryByActor = new LinkedHashMap<>();
        queryByActor.put("filter", byActorClause);
        queryByActor.put("page", 1);
        queryByActor.put("pageSize", 10);
        ResponseEntity<Map> queryActorResp = restTemplate.exchange(
                baseUrl + "/v1/tenants/" + tenantId + "/entities/" + entityId + "/records/query",
                HttpMethod.POST,
                new HttpEntity<>(queryByActor, headers),
                Map.class
        );
        assertThat(queryActorResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> actorItems =
                (List<Map<String, Object>>) (List<?>) queryActorResp.getBody().get("items");
        assertThat(actorItems).hasSize(1);
        assertThat(actorItems.get(0).get("id").toString()).isEqualTo(recordId);
        assertRecordMetadataPresentAfterCreate(actorItems.get(0), userId);

        // Structured query: filter by record.created_at (always in the past for this row)
        Map<String, Object> byTimeClause = new LinkedHashMap<>();
        byTimeClause.put("field", "record.created_at");
        byTimeClause.put("op", "gte");
        byTimeClause.put("value", "1970-01-01T00:00:00Z");
        Map<String, Object> queryByTime = new LinkedHashMap<>();
        queryByTime.put("filter", byTimeClause);
        queryByTime.put("page", 1);
        queryByTime.put("pageSize", 10);
        queryByTime.put("sort", Map.of("field", "record.created_at", "direction", "asc"));
        ResponseEntity<Map> queryTimeResp = restTemplate.exchange(
                baseUrl + "/v1/tenants/" + tenantId + "/entities/" + entityId + "/records/query",
                HttpMethod.POST,
                new HttpEntity<>(queryByTime, headers),
                Map.class
        );
        assertThat(queryTimeResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> timeItems =
                (List<Map<String, Object>>) (List<?>) queryTimeResp.getBody().get("items");
        assertThat(timeItems).hasSize(1);
        assertThat(timeItems.get(0).get("id").toString()).isEqualTo(recordId);

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
        @SuppressWarnings("unchecked")
        Map<String, Object> updatedBody = (Map<String, Object>) updateResp.getBody();
        assertThat(updatedBody.get("values")).isNotNull();
        assertRecordMetadataAfterUpdate(updatedBody, userId);

        // Structured query: record.updated_by set after PATCH
        Map<String, Object> byUpdaterClause = new LinkedHashMap<>();
        byUpdaterClause.put("field", "record.updated_by");
        byUpdaterClause.put("op", "eq");
        byUpdaterClause.put("value", userId.toString());
        Map<String, Object> queryByUpdater = new LinkedHashMap<>();
        queryByUpdater.put("filter", byUpdaterClause);
        queryByUpdater.put("page", 1);
        queryByUpdater.put("pageSize", 10);
        ResponseEntity<Map> queryUpdaterResp = restTemplate.exchange(
                baseUrl + "/v1/tenants/" + tenantId + "/entities/" + entityId + "/records/query",
                HttpMethod.POST,
                new HttpEntity<>(queryByUpdater, headers),
                Map.class
        );
        assertThat(queryUpdaterResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> updaterItems =
                (List<Map<String, Object>>) (List<?>) queryUpdaterResp.getBody().get("items");
        assertThat(updaterItems).hasSize(1);
        assertThat(updaterItems.get(0).get("id").toString()).isEqualTo(recordId);
        assertRecordMetadataAfterUpdate(updaterItems.get(0), userId);

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

    @Test
    void endToEnd_requiredOptimisticVersionFieldAutoManaged() {
        Assumptions.assumeTrue(jdbcTemplate != null, "E2E skipped: no CockroachDB connection");

        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        var headers = authHeaders(userId, tenantId, RECORDS_PERMS);

        Map<String, Object> createEntity = Map.of("name", "Versioned", "slug", "versioned_ent", "status", "ACTIVE");
        ResponseEntity<Map> entityResp = restTemplate.exchange(
                baseUrl + "/v1/entities",
                HttpMethod.POST,
                new HttpEntity<>(createEntity, headers),
                Map.class
        );
        assertThat(entityResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String entityId = String.valueOf(entityResp.getBody().get("id"));

        Map<String, Object> createVersionField = new LinkedHashMap<>();
        createVersionField.put("name", "Version");
        createVersionField.put("slug", "version");
        createVersionField.put("fieldType", "number");
        createVersionField.put("required", true);
        ResponseEntity<Map> fieldResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + entityId + "/fields",
                HttpMethod.POST,
                new HttpEntity<>(createVersionField, headers),
                Map.class
        );
        assertThat(fieldResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> values = Map.of("name", "v1 row");
        Map<String, Object> create = Map.of("values", values);
        ResponseEntity<Map> createRec = restTemplate.exchange(
                baseUrl + "/v1/tenants/" + tenantId + "/entities/" + entityId + "/records",
                HttpMethod.POST,
                new HttpEntity<>(create, headers),
                Map.class
        );
        assertThat(createRec.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("unchecked")
        Map<String, Object> created = (Map<String, Object>) createRec.getBody();
        @SuppressWarnings("unchecked")
        Map<String, Object> createdVals = (Map<String, Object>) created.get("values");
        assertThat(new BigDecimal(createdVals.get("version").toString())).isEqualByComparingTo(BigDecimal.ZERO);

        String recordId = String.valueOf(created.get("id"));

        ResponseEntity<Map> patch1 = restTemplate.exchange(
                baseUrl + "/v1/tenants/" + tenantId + "/entities/" + entityId + "/records/" + recordId,
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("values", Map.of("name", "v1 row b")), headers),
                Map.class
        );
        assertThat(patch1.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> after1 = (Map<String, Object>) ((Map<String, Object>) patch1.getBody()).get("values");
        assertThat(new BigDecimal(after1.get("version").toString())).isEqualByComparingTo(BigDecimal.ONE);

        ResponseEntity<Map> conflict = restTemplate.exchange(
                baseUrl + "/v1/tenants/" + tenantId + "/entities/" + entityId + "/records/" + recordId,
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("values", Map.of("version", 0, "name", "stale")), headers),
                Map.class
        );
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    /**
     * Record API must expose row metadata (creator, timestamps, optional labels). Labels are optional when IAM
     * user rows are absent; timestamps and createdBy must always be present after create.
     */
    private static void assertRecordMetadataPresentAfterCreate(Map<String, Object> body, UUID userId) {
        assertThat(body.get("createdBy")).isEqualTo(userId.toString());
        assertThat(body.get("createdAt")).isNotNull();
        assertThat(body.get("updatedAt")).isNotNull();
        // Jackson may omit nulls; accept absent or null for fields not set on create
        assertThat(body.get("updatedBy")).isNull();
        Object cbl = body.get("createdByLabel");
        assertThat(cbl == null || cbl instanceof String).isTrue();
        Object ubl = body.get("updatedByLabel");
        assertThat(ubl == null || ubl instanceof String).isTrue();
    }

    private static void assertRecordMetadataAfterUpdate(Map<String, Object> body, UUID userId) {
        assertThat(body.get("createdBy")).isEqualTo(userId.toString());
        assertThat(body.get("updatedBy")).isEqualTo(userId.toString());
        assertThat(body.get("createdAt")).isNotNull();
        assertThat(body.get("updatedAt")).isNotNull();
        Object cbl = body.get("createdByLabel");
        assertThat(cbl == null || cbl instanceof String).isTrue();
        Object ubl = body.get("updatedByLabel");
        assertThat(ubl == null || ubl instanceof String).isTrue();
    }
}

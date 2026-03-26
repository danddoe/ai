package com.erp.entitybuilder.e2e;

import com.erp.entitybuilder.service.catalog.EntityStatusDynamicEntityProvisioner;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * System (E2E) tests for {@code EntityStatusAssignment}: HTTP API and persistence when CockroachDB is available.
 */
class EntityStatusAssignmentE2ETest extends AbstractEntityBuilderE2ETest {

    private static final UUID PLATFORM_TENANT_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_TENANT_ID = UUID.randomUUID();

    private static final List<String> PLATFORM_SCHEMA_WRITE = List.of(
            "entity_builder:schema:read",
            "entity_builder:schema:write"
    );

    private static final List<String> TENANT_SCHEMA_READ = List.of("entity_builder:schema:read");

    private static final List<String> TENANT_SCHEMA_WRITE = List.of(
            "entity_builder:schema:read",
            "entity_builder:schema:tenant_write"
    );

    @Override
    protected List<String> extraSpringBootArgs() {
        return List.of("--entitybuilder.platform.tenant-id=" + PLATFORM_TENANT_ID);
    }

    @Test
    void entityStatusAssignments_crudFlow_listAvailable_put_replace_get_clear() {
        Assumptions.assumeTrue(jdbcTemplate != null, "E2E skipped: no CockroachDB connection");

        UUID userId = UUID.randomUUID();
        var platformHeaders = authHeaders(userId, PLATFORM_TENANT_ID, PLATFORM_SCHEMA_WRITE);
        ResponseEntity<Void> ensureResp = restTemplate.exchange(
                baseUrl + "/v1/tenants/" + PLATFORM_TENANT_ID + "/platform/entity-status/ensure",
                HttpMethod.POST,
                new HttpEntity<>(null, platformHeaders),
                Void.class
        );
        assertThat(ensureResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        var writeHeaders = authHeaders(userId, CUSTOMER_TENANT_ID, TENANT_SCHEMA_WRITE);
        String slug = "esa_e2e_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        ResponseEntity<Map> createEntityResp = restTemplate.postForEntity(
                baseUrl + "/v1/entities",
                new HttpEntity<>(Map.of(
                        "name", "Entity status assignment E2E",
                        "slug", slug,
                        "description", "",
                        "status", "ACTIVE"
                ), writeHeaders),
                Map.class
        );
        assertThat(createEntityResp.getStatusCode().is2xxSuccessful()).isTrue();
        UUID hostEntityId = UUID.fromString(String.valueOf(createEntityResp.getBody().get("id")));

        String baseAssign = baseUrl + "/v1/tenants/" + CUSTOMER_TENANT_ID + "/entities/" + hostEntityId + "/entity-status-assignments";

        ResponseEntity<List> listEmptyResp = restTemplate.exchange(
                baseAssign,
                HttpMethod.GET,
                new HttpEntity<>(null, writeHeaders),
                List.class
        );
        assertThat(listEmptyResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listEmptyResp.getBody()).isEmpty();

        ResponseEntity<List> availableResp = restTemplate.exchange(
                baseAssign + "/available",
                HttpMethod.GET,
                new HttpEntity<>(null, writeHeaders),
                List.class
        );
        assertThat(availableResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(availableResp.getBody()).isNotEmpty();

        UUID activeId = EntityStatusDynamicEntityProvisioner.deterministicStatusId(PLATFORM_TENANT_ID, "ACTIVE");
        UUID inactiveId = EntityStatusDynamicEntityProvisioner.deterministicStatusId(PLATFORM_TENANT_ID, "INACTIVE");
        UUID deletedId = EntityStatusDynamicEntityProvisioner.deterministicStatusId(PLATFORM_TENANT_ID, "DELETED");

        ResponseEntity<List> putResp = restTemplate.exchange(
                baseAssign,
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("entityStatusIds", List.of(inactiveId, activeId)), writeHeaders),
                List.class
        );
        assertThat(putResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> afterPut = (List<Map<String, Object>>) putResp.getBody();
        assertThat(afterPut).hasSize(2);
        assertThat(afterPut.get(0).get("sortOrder")).isEqualTo(0);
        assertThat(afterPut.get(1).get("sortOrder")).isEqualTo(1);
        assertThat(UUID.fromString(String.valueOf(afterPut.get(0).get("entityStatusId")))).isEqualTo(inactiveId);
        assertThat(UUID.fromString(String.valueOf(afterPut.get(1).get("entityStatusId")))).isEqualTo(activeId);
        assertThat(String.valueOf(afterPut.get(0).get("code"))).isEqualTo("INACTIVE");
        assertThat(String.valueOf(afterPut.get(1).get("code"))).isEqualTo("ACTIVE");

        ResponseEntity<List> listAfterPutResp = restTemplate.exchange(
                baseAssign,
                HttpMethod.GET,
                new HttpEntity<>(null, writeHeaders),
                List.class
        );
        assertThat(listAfterPutResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listAfterPutResp.getBody()).hasSize(2);

        ResponseEntity<List> putReorderResp = restTemplate.exchange(
                baseAssign,
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("entityStatusIds", List.of(activeId, inactiveId, deletedId)), writeHeaders),
                List.class
        );
        assertThat(putReorderResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> reordered = (List<Map<String, Object>>) putReorderResp.getBody();
        assertThat(reordered).hasSize(3);
        assertThat(UUID.fromString(String.valueOf(reordered.get(0).get("entityStatusId")))).isEqualTo(activeId);
        assertThat(UUID.fromString(String.valueOf(reordered.get(2).get("entityStatusId")))).isEqualTo(deletedId);

        int rowsInDb = jdbcTemplate.queryForObject(
                "SELECT count(*)::int FROM entity_status_assignment WHERE tenant_id = ? "
                        + "AND assignment_scope = 'ENTITY_DEFINITION' AND scope_id = ?",
                Integer.class,
                CUSTOMER_TENANT_ID,
                hostEntityId
        );
        assertThat(rowsInDb).isEqualTo(3);

        ResponseEntity<List> putClearResp = restTemplate.exchange(
                baseAssign,
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("entityStatusIds", List.of()), writeHeaders),
                List.class
        );
        assertThat(putClearResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(putClearResp.getBody()).isEmpty();

        int rowsAfterClear = jdbcTemplate.queryForObject(
                "SELECT count(*)::int FROM entity_status_assignment WHERE tenant_id = ? "
                        + "AND assignment_scope = 'ENTITY_DEFINITION' AND scope_id = ?",
                Integer.class,
                CUSTOMER_TENANT_ID,
                hostEntityId
        );
        assertThat(rowsAfterClear).isZero();
    }

    @Test
    void fieldScopedAssignments_recordListUsesFieldScopeWithFallbackToEntityScope() {
        Assumptions.assumeTrue(jdbcTemplate != null, "E2E skipped: no CockroachDB connection");

        UUID userId = UUID.randomUUID();
        var platformHeaders = authHeaders(userId, PLATFORM_TENANT_ID, PLATFORM_SCHEMA_WRITE);
        assertThat(restTemplate.exchange(
                baseUrl + "/v1/tenants/" + PLATFORM_TENANT_ID + "/platform/entity-status/ensure",
                HttpMethod.POST,
                new HttpEntity<>(null, platformHeaders),
                Void.class
        ).getStatusCode()).isEqualTo(HttpStatus.OK);

        var writeHeaders = authHeaders(userId, CUSTOMER_TENANT_ID, TENANT_SCHEMA_WRITE);
        var recordRead = authHeaders(userId, CUSTOMER_TENANT_ID, List.of(
                "entity_builder:schema:read",
                "entity_builder:records:read"
        ));

        String slug = "esa_field_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        ResponseEntity<Map> createEntityResp = restTemplate.postForEntity(
                baseUrl + "/v1/entities",
                new HttpEntity<>(Map.of(
                        "name", "Entity status assignment field E2E",
                        "slug", slug,
                        "description", "",
                        "status", "ACTIVE"
                ), writeHeaders),
                Map.class
        );
        assertThat(createEntityResp.getStatusCode().is2xxSuccessful()).isTrue();
        UUID hostEntityId = UUID.fromString(String.valueOf(createEntityResp.getBody().get("id")));

        Map<String, Object> refFieldBody = new java.util.LinkedHashMap<>();
        refFieldBody.put("name", "Status ref");
        refFieldBody.put("slug", "status_ref");
        refFieldBody.put("fieldType", "reference");
        refFieldBody.put("required", false);
        refFieldBody.put("config", Map.of("targetEntitySlug", "entity_status"));

        ResponseEntity<Map> createFieldResp = restTemplate.postForEntity(
                baseUrl + "/v1/entities/" + hostEntityId + "/fields",
                new HttpEntity<>(refFieldBody, writeHeaders),
                Map.class
        );
        assertThat(createFieldResp.getStatusCode().is2xxSuccessful()).isTrue();
        UUID statusRefFieldId = UUID.fromString(String.valueOf(createFieldResp.getBody().get("id")));

        UUID activeId = EntityStatusDynamicEntityProvisioner.deterministicStatusId(PLATFORM_TENANT_ID, "ACTIVE");
        UUID inactiveId = EntityStatusDynamicEntityProvisioner.deterministicStatusId(PLATFORM_TENANT_ID, "INACTIVE");

        String entityAssignBase = baseUrl + "/v1/tenants/" + CUSTOMER_TENANT_ID + "/entities/" + hostEntityId + "/entity-status-assignments";
        assertThat(restTemplate.exchange(
                entityAssignBase,
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("entityStatusIds", List.of(inactiveId, activeId)), writeHeaders),
                List.class
        ).getStatusCode()).isEqualTo(HttpStatus.OK);

        String fieldAssignBase = baseUrl + "/v1/entities/" + hostEntityId + "/fields/" + statusRefFieldId + "/entity-status-assignments";
        assertThat(restTemplate.exchange(
                fieldAssignBase,
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("entityStatusIds", List.of(activeId)), writeHeaders),
                List.class
        ).getStatusCode()).isEqualTo(HttpStatus.OK);

        int fieldRows = jdbcTemplate.queryForObject(
                "SELECT count(*)::int FROM entity_status_assignment WHERE tenant_id = ? "
                        + "AND assignment_scope = 'ENTITY_FIELD' AND scope_id = ?",
                Integer.class,
                CUSTOMER_TENANT_ID,
                statusRefFieldId
        );
        assertThat(fieldRows).isEqualTo(1);

        ResponseEntity<Map> entityStatusDefResp = restTemplate.exchange(
                baseUrl + "/v1/entities/by-slug/entity_status",
                HttpMethod.GET,
                new HttpEntity<>(null, recordRead),
                Map.class
        );
        assertThat(entityStatusDefResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        UUID entityStatusEntityId = UUID.fromString(String.valueOf(entityStatusDefResp.getBody().get("id")));

        String listFieldScoped = baseUrl + "/v1/tenants/" + CUSTOMER_TENANT_ID + "/entities/" + entityStatusEntityId
                + "/records?page=1&pageSize=50&assignedForEntityId=" + hostEntityId
                + "&assignedForEntityFieldId=" + statusRefFieldId;
        ResponseEntity<Map> listFieldScopedResp = restTemplate.exchange(
                listFieldScoped,
                HttpMethod.GET,
                new HttpEntity<>(null, recordRead),
                Map.class
        );
        assertThat(listFieldScopedResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> itemsField = (List<Map<String, Object>>) listFieldScopedResp.getBody().get("items");
        assertThat(itemsField).hasSize(1);

        assertThat(restTemplate.exchange(
                fieldAssignBase,
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("entityStatusIds", List.of()), writeHeaders),
                List.class
        ).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> listFallbackResp = restTemplate.exchange(
                listFieldScoped,
                HttpMethod.GET,
                new HttpEntity<>(null, recordRead),
                Map.class
        );
        assertThat(listFallbackResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> itemsFallback = (List<Map<String, Object>>) listFallbackResp.getBody().get("items");
        assertThat(itemsFallback).hasSize(2);
    }

    @Test
    void entityStatusAssignments_putForbiddenWithoutTenantSchemaWrite() {
        Assumptions.assumeTrue(jdbcTemplate != null, "E2E skipped: no CockroachDB connection");

        UUID userId = UUID.randomUUID();
        var platformHeaders = authHeaders(userId, PLATFORM_TENANT_ID, PLATFORM_SCHEMA_WRITE);
        assertThat(restTemplate.exchange(
                baseUrl + "/v1/tenants/" + PLATFORM_TENANT_ID + "/platform/entity-status/ensure",
                HttpMethod.POST,
                new HttpEntity<>(null, platformHeaders),
                Void.class
        ).getStatusCode()).isEqualTo(HttpStatus.OK);

        var readOnlyHeaders = authHeaders(userId, CUSTOMER_TENANT_ID, TENANT_SCHEMA_READ);
        String slug = "esa_ro_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        ResponseEntity<Map> createResp = restTemplate.postForEntity(
                baseUrl + "/v1/entities",
                new HttpEntity<>(Map.of(
                        "name", "ESA read-only host",
                        "slug", slug,
                        "description", "",
                        "status", "ACTIVE"
                ), authHeaders(userId, CUSTOMER_TENANT_ID, TENANT_SCHEMA_WRITE)),
                Map.class
        );
        assertThat(createResp.getStatusCode().is2xxSuccessful()).isTrue();
        UUID hostEntityId = UUID.fromString(String.valueOf(createResp.getBody().get("id")));

        UUID activeId = EntityStatusDynamicEntityProvisioner.deterministicStatusId(PLATFORM_TENANT_ID, "ACTIVE");
        String url = baseUrl + "/v1/tenants/" + CUSTOMER_TENANT_ID + "/entities/" + hostEntityId + "/entity-status-assignments";

        ResponseEntity<List> getOk = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(null, readOnlyHeaders), List.class);
        assertThat(getOk.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<List> getAvailOk = restTemplate.exchange(
                url + "/available", HttpMethod.GET, new HttpEntity<>(null, readOnlyHeaders), List.class);
        assertThat(getAvailOk.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> putForbidden = restTemplate.exchange(
                url,
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("entityStatusIds", List.of(activeId)), readOnlyHeaders),
                String.class
        );
        assertThat(putForbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}

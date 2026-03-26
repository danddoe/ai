package com.erp.entitybuilder.e2e;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import com.erp.entitybuilder.service.catalog.EntityStatusDynamicEntityProvisioner;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EntityStatusProvisionE2ETest extends AbstractEntityBuilderE2ETest {

    private static final UUID PLATFORM_TENANT_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_TENANT_ID = UUID.randomUUID();

    private static final List<String> SCHEMA_PERMS = List.of(
            "entity_builder:schema:read",
            "entity_builder:schema:write"
    );

    private static final List<String> RECORD_READ = List.of(
            "entity_builder:schema:read",
            "entity_builder:records:read"
    );

    private static final List<String> TENANT_SCHEMA_WRITE = List.of(
            "entity_builder:schema:read",
            "entity_builder:schema:tenant_write",
            "entity_builder:records:read"
    );

    @Override
    protected List<String> extraSpringBootArgs() {
        return List.of("--entitybuilder.platform.tenant-id=" + PLATFORM_TENANT_ID);
    }

    @Test
    void entityStatusEnsure_seedsStandardRowsVisibleToOtherTenants() {
        Assumptions.assumeTrue(jdbcTemplate != null, "E2E skipped: no CockroachDB connection");

        UUID userId = UUID.randomUUID();
        var provisionHeaders = authHeaders(userId, PLATFORM_TENANT_ID, SCHEMA_PERMS);

        ResponseEntity<Void> ensureResp = restTemplate.exchange(
                baseUrl + "/v1/tenants/" + PLATFORM_TENANT_ID + "/platform/entity-status/ensure",
                HttpMethod.POST,
                new HttpEntity<>(null, provisionHeaders),
                Void.class
        );
        assertThat(ensureResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        Integer statusCount = jdbcTemplate.queryForObject(
                "SELECT count(*)::int FROM entity_status WHERE tenant_id = ? AND record_scope = 'STANDARD_RECORD'",
                Integer.class,
                PLATFORM_TENANT_ID
        );
        assertThat(statusCount).isEqualTo(4);

        Integer transCount = jdbcTemplate.queryForObject(
                "SELECT count(*)::int FROM entity_status_transition WHERE tenant_id = ?",
                Integer.class,
                PLATFORM_TENANT_ID
        );
        assertThat(transCount).isGreaterThanOrEqualTo(9);

        var customerHeaders = authHeaders(userId, CUSTOMER_TENANT_ID, RECORD_READ);
        ResponseEntity<Map> entityResp = restTemplate.exchange(
                baseUrl + "/v1/entities/by-slug/entity_status",
                HttpMethod.GET,
                new HttpEntity<>(null, customerHeaders),
                Map.class
        );
        assertThat(entityResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String entityIdStr = String.valueOf(entityResp.getBody().get("id"));
        UUID entityStatusEntityId = UUID.fromString(entityIdStr);

        ResponseEntity<Map> listResp = restTemplate.exchange(
                baseUrl + "/v1/tenants/" + CUSTOMER_TENANT_ID + "/entities/" + entityStatusEntityId + "/records?page=1&pageSize=50",
                HttpMethod.GET,
                new HttpEntity<>(null, customerHeaders),
                Map.class
        );
        assertThat(listResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) listResp.getBody().get("items");
        assertThat(items).hasSize(4);
    }

    @Test
    void assignedForEntityId_filtersEntityStatusRecordsWhenAssignmentsConfigured() {
        Assumptions.assumeTrue(jdbcTemplate != null, "E2E skipped: no CockroachDB connection");

        UUID userId = UUID.randomUUID();
        var provisionHeaders = authHeaders(userId, PLATFORM_TENANT_ID, SCHEMA_PERMS);
        ResponseEntity<Void> ensureResp = restTemplate.exchange(
                baseUrl + "/v1/tenants/" + PLATFORM_TENANT_ID + "/platform/entity-status/ensure",
                HttpMethod.POST,
                new HttpEntity<>(null, provisionHeaders),
                Void.class
        );
        assertThat(ensureResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        var customerWriteHeaders = authHeaders(userId, CUSTOMER_TENANT_ID, TENANT_SCHEMA_WRITE);
        String slug = "asgn_e2e_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Map<String, Object> createBody = Map.of(
                "name", "Assignment E2E",
                "slug", slug,
                "description", "",
                "status", "ACTIVE"
        );
        ResponseEntity<Map> createResp = restTemplate.postForEntity(
                baseUrl + "/v1/entities",
                new HttpEntity<>(createBody, customerWriteHeaders),
                Map.class
        );
        assertThat(createResp.getStatusCode().is2xxSuccessful()).isTrue();
        UUID tenantEntityId = UUID.fromString(String.valueOf(createResp.getBody().get("id")));

        UUID activeId = EntityStatusDynamicEntityProvisioner.deterministicStatusId(PLATFORM_TENANT_ID, "ACTIVE");
        UUID inactiveId = EntityStatusDynamicEntityProvisioner.deterministicStatusId(PLATFORM_TENANT_ID, "INACTIVE");

        Map<String, Object> putBody = Map.of("entityStatusIds", List.of(activeId, inactiveId));
        ResponseEntity<Void> putResp = restTemplate.exchange(
                baseUrl + "/v1/tenants/" + CUSTOMER_TENANT_ID + "/entities/" + tenantEntityId + "/entity-status-assignments",
                HttpMethod.PUT,
                new HttpEntity<>(putBody, customerWriteHeaders),
                Void.class
        );
        assertThat(putResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        var customerRead = authHeaders(userId, CUSTOMER_TENANT_ID, RECORD_READ);
        ResponseEntity<Map> entityResp = restTemplate.exchange(
                baseUrl + "/v1/entities/by-slug/entity_status",
                HttpMethod.GET,
                new HttpEntity<>(null, customerRead),
                Map.class
        );
        assertThat(entityResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        UUID entityStatusEntityId = UUID.fromString(String.valueOf(entityResp.getBody().get("id")));

        String listUrl = baseUrl + "/v1/tenants/" + CUSTOMER_TENANT_ID + "/entities/" + entityStatusEntityId
                + "/records?page=1&pageSize=50&assignedForEntityId=" + tenantEntityId;
        ResponseEntity<Map> listResp = restTemplate.exchange(
                listUrl,
                HttpMethod.GET,
                new HttpEntity<>(null, customerRead),
                Map.class
        );
        assertThat(listResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) listResp.getBody().get("items");
        assertThat(items).hasSize(2);
    }
}

package com.erp.entitybuilder.e2e;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EntitiesSchemaE2ETest extends AbstractEntityBuilderE2ETest {

    private static final List<String> SCHEMA_PERMS = List.of(
            "entity_builder:schema:read",
            "entity_builder:schema:write"
    );

    @Test
    void endToEnd_entitiesCrud() {
        Assumptions.assumeTrue(jdbcTemplate != null, "E2E skipped: no CockroachDB connection");

        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        var headers = authHeaders(userId, tenantId, SCHEMA_PERMS);

        // Create entity
        Map<String, Object> create = new HashMap<>();
        create.put("name", "Customer");
        create.put("slug", "customer");
        create.put("description", "Customer entity");
        create.put("status", "ACTIVE");
        create.put("categoryKey", "accounts_receivable");
        ResponseEntity<Map> createResp = restTemplate.exchange(
                baseUrl + "/v1/entities",
                HttpMethod.POST,
                new HttpEntity<>(create, headers),
                Map.class
        );
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String entityId = String.valueOf(createResp.getBody().get("id"));
        assertThat(entityId).isNotBlank();
        assertThat(createResp.getBody().get("name")).isEqualTo("Customer");
        assertThat(createResp.getBody().get("slug")).isEqualTo("customer");
        assertThat(createResp.getBody().get("categoryKey")).isEqualTo("accounts_receivable");
        assertThat(createResp.getBody().get("defaultDisplayFieldSlug")).isEqualTo("name");
        assertThat(createResp.getBody().get("definitionScope")).isEqualTo("TENANT_OBJECT");

        // Invalid categoryKey on create
        Map<String, Object> badCat = new HashMap<>();
        badCat.put("name", "Bad");
        badCat.put("slug", "bad-cat");
        badCat.put("status", "ACTIVE");
        badCat.put("categoryKey", "unknown_module");
        ResponseEntity<Map> badResp = restTemplate.exchange(
                baseUrl + "/v1/entities",
                HttpMethod.POST,
                new HttpEntity<>(badCat, headers),
                Map.class
        );
        assertThat(badResp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // List filtered by categoryKey
        ResponseEntity<List> listAr = restTemplate.exchange(
                baseUrl + "/v1/entities?categoryKey=accounts_receivable",
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                List.class
        );
        assertThat(listAr.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listAr.getBody()).hasSize(1);
        assertThat(((Map<?, ?>) listAr.getBody().get(0)).get("slug")).isEqualTo("customer");

        ResponseEntity<List> listAp = restTemplate.exchange(
                baseUrl + "/v1/entities?categoryKey=accounts_payable",
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                List.class
        );
        assertThat(listAp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listAp.getBody()).isEmpty();

        // Search by q (name contains)
        ResponseEntity<List> listQ = restTemplate.exchange(
                baseUrl + "/v1/entities?q=Cust",
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                List.class
        );
        assertThat(listQ.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listQ.getBody()).hasSize(1);
        assertThat(((Map<?, ?>) listQ.getBody().get(0)).get("slug")).isEqualTo("customer");

        // Get entity
        ResponseEntity<Map> getResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + entityId,
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                Map.class
        );
        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResp.getBody().get("name")).isEqualTo("Customer");
        assertThat(getResp.getBody().get("definitionScope")).isEqualTo("TENANT_OBJECT");

        Map<String, Object> promoteScope = new HashMap<>();
        promoteScope.put("definitionScope", "STANDARD_OBJECT");
        ResponseEntity<Map> scopeResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + entityId,
                HttpMethod.PATCH,
                new HttpEntity<>(promoteScope, headers),
                Map.class
        );
        assertThat(scopeResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(scopeResp.getBody().get("definitionScope")).isEqualTo("STANDARD_OBJECT");
        ResponseEntity<Map> getScopeResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + entityId,
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                Map.class
        );
        assertThat(getScopeResp.getBody().get("definitionScope")).isEqualTo("STANDARD_OBJECT");

        // Update entity
        Map<String, Object> update = new HashMap<>();
        update.put("name", "Customer Updated");
        update.put("description", "Updated description");
        update.put("categoryKey", "general_ledger");
        ResponseEntity<Map> updateResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + entityId,
                HttpMethod.PATCH,
                new HttpEntity<>(update, headers),
                Map.class
        );
        assertThat(updateResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResp.getBody().get("name")).isEqualTo("Customer Updated");
        assertThat(updateResp.getBody().get("categoryKey")).isEqualTo("general_ledger");

        Map<String, Object> clearCat = Map.of("clearCategoryKey", true);
        ResponseEntity<Map> clearResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + entityId,
                HttpMethod.PATCH,
                new HttpEntity<>(clearCat, headers),
                Map.class
        );
        assertThat(clearResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(clearResp.getBody().get("categoryKey")).isNull();

        // Delete entity
        ResponseEntity<Void> deleteResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + entityId,
                HttpMethod.DELETE,
                new HttpEntity<>(null, headers),
                Void.class
        );
        assertThat(deleteResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify deleted
        ResponseEntity<Map> getAfterResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + entityId,
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                Map.class
        );
        assertThat(getAfterResp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void schema_write_without_read_can_list_and_get_entities() {
        Assumptions.assumeTrue(jdbcTemplate != null, "E2E skipped: no CockroachDB connection");

        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        var headersWriteOnly = authHeaders(userId, tenantId, List.of("entity_builder:schema:write"));

        Map<String, Object> create = new HashMap<>();
        create.put("name", "WriteOnlyList");
        create.put("slug", "write_only_list_e2e");
        create.put("status", "ACTIVE");
        ResponseEntity<Map> createResp = restTemplate.exchange(
                baseUrl + "/v1/entities",
                HttpMethod.POST,
                new HttpEntity<>(create, headersWriteOnly),
                Map.class
        );
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String entityId = String.valueOf(createResp.getBody().get("id"));

        ResponseEntity<List> listResp = restTemplate.exchange(
                baseUrl + "/v1/entities",
                HttpMethod.GET,
                new HttpEntity<>(null, headersWriteOnly),
                List.class
        );
        assertThat(listResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResp.getBody()).isNotEmpty();

        ResponseEntity<Map> getResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + entityId,
                HttpMethod.GET,
                new HttpEntity<>(null, headersWriteOnly),
                Map.class
        );
        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResp.getBody().get("slug")).isEqualTo("write_only_list_e2e");
    }

    @Test
    void portal_navigation_write_can_list_entities() {
        Assumptions.assumeTrue(jdbcTemplate != null, "E2E skipped: no CockroachDB connection");

        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        var headersNav = authHeaders(userId, tenantId, List.of("portal:navigation:write"));

        ResponseEntity<List> listResp = restTemplate.exchange(
                baseUrl + "/v1/entities",
                HttpMethod.GET,
                new HttpEntity<>(null, headersNav),
                List.class
        );
        assertThat(listResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}

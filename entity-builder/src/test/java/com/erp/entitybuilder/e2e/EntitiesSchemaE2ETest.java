package com.erp.entitybuilder.e2e;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

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
        Map<String, Object> create = Map.of(
                "name", "Customer",
                "slug", "customer",
                "description", "Customer entity",
                "status", "ACTIVE"
        );
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

        // Get entity
        ResponseEntity<Map> getResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + entityId,
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                Map.class
        );
        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResp.getBody().get("name")).isEqualTo("Customer");

        // Update entity
        Map<String, Object> update = Map.of("name", "Customer Updated", "description", "Updated description");
        ResponseEntity<Map> updateResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + entityId,
                HttpMethod.PATCH,
                new HttpEntity<>(update, headers),
                Map.class
        );
        assertThat(updateResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResp.getBody().get("name")).isEqualTo("Customer Updated");

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
}

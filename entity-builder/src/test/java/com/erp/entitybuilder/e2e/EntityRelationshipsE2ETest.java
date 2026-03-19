package com.erp.entitybuilder.e2e;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EntityRelationshipsE2ETest extends AbstractEntityBuilderE2ETest {

    private static final List<String> SCHEMA_PERMS = List.of(
            "entity_builder:schema:read",
            "entity_builder:schema:write"
    );

    @Test
    void endToEnd_entityRelationshipsCrud() {
        Assumptions.assumeTrue(jdbcTemplate != null, "E2E skipped: no CockroachDB connection");

        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        var headers = authHeaders(userId, tenantId, SCHEMA_PERMS);

        // Create two entities for relationship
        Map<String, Object> orderEntity = Map.of("name", "Order", "slug", "order", "status", "ACTIVE");
        ResponseEntity<Map> orderResp = restTemplate.exchange(
                baseUrl + "/v1/entities",
                HttpMethod.POST,
                new HttpEntity<>(orderEntity, headers),
                Map.class
        );
        assertThat(orderResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String fromEntityId = String.valueOf(orderResp.getBody().get("id"));

        Map<String, Object> customerEntity = Map.of("name", "Customer", "slug", "customer", "status", "ACTIVE");
        ResponseEntity<Map> customerResp = restTemplate.exchange(
                baseUrl + "/v1/entities",
                HttpMethod.POST,
                new HttpEntity<>(customerEntity, headers),
                Map.class
        );
        assertThat(customerResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String toEntityId = String.valueOf(customerResp.getBody().get("id"));

        // Create relationship
        Map<String, Object> create = Map.of(
                "name", "Order to Customer",
                "slug", "order-customer",
                "cardinality", "one-to-many",
                "fromEntityId", fromEntityId,
                "toEntityId", toEntityId
        );
        ResponseEntity<Map> createResp = restTemplate.exchange(
                baseUrl + "/v1/entity-relationships",
                HttpMethod.POST,
                new HttpEntity<>(create, headers),
                Map.class
        );
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String relationshipId = String.valueOf(createResp.getBody().get("id"));
        assertThat(createResp.getBody().get("slug")).isEqualTo("order-customer");
        assertThat(createResp.getBody().get("cardinality")).isEqualTo("one-to-many");

        // List relationships
        ResponseEntity<List> listResp = restTemplate.exchange(
                baseUrl + "/v1/entity-relationships",
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                List.class
        );
        assertThat(listResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResp.getBody()).hasSize(1);

        // Get relationship
        ResponseEntity<Map> getResp = restTemplate.exchange(
                baseUrl + "/v1/entity-relationships/" + relationshipId,
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                Map.class
        );
        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResp.getBody().get("name")).isEqualTo("Order to Customer");

        // Update relationship
        Map<String, Object> update = Map.of("name", "Order Belongs To Customer");
        ResponseEntity<Map> updateResp = restTemplate.exchange(
                baseUrl + "/v1/entity-relationships/" + relationshipId,
                HttpMethod.PATCH,
                new HttpEntity<>(update, headers),
                Map.class
        );
        assertThat(updateResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResp.getBody().get("name")).isEqualTo("Order Belongs To Customer");

        // Delete relationship
        ResponseEntity<Void> deleteResp = restTemplate.exchange(
                baseUrl + "/v1/entity-relationships/" + relationshipId,
                HttpMethod.DELETE,
                new HttpEntity<>(null, headers),
                Void.class
        );
        assertThat(deleteResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify deleted
        ResponseEntity<Map> getAfterResp = restTemplate.exchange(
                baseUrl + "/v1/entity-relationships/" + relationshipId,
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                Map.class
        );
        assertThat(getAfterResp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}

package com.erp.entitybuilder.e2e;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RecordLinksE2ETest extends AbstractEntityBuilderE2ETest {

    private static final List<String> RECORDS_PERMS = List.of(
            "entity_builder:schema:read",
            "entity_builder:schema:write",
            "entity_builder:records:read",
            "entity_builder:records:write"
    );

    @Test
    void endToEnd_recordLinksCrud() {
        Assumptions.assumeTrue(jdbcTemplate != null, "E2E skipped: no CockroachDB connection");

        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        var headers = authHeaders(userId, tenantId, RECORDS_PERMS);

        // Create two entities and a relationship
        Map<String, Object> orderEntity = Map.of("name", "Order", "slug", "order", "status", "ACTIVE");
        ResponseEntity<Map> orderResp = restTemplate.exchange(
                baseUrl + "/v1/entities",
                HttpMethod.POST,
                new HttpEntity<>(orderEntity, headers),
                Map.class
        );
        String orderEntityId = String.valueOf(orderResp.getBody().get("id"));

        Map<String, Object> itemEntity = Map.of("name", "Item", "slug", "item", "status", "ACTIVE");
        ResponseEntity<Map> itemResp = restTemplate.exchange(
                baseUrl + "/v1/entities",
                HttpMethod.POST,
                new HttpEntity<>(itemEntity, headers),
                Map.class
        );
        String itemEntityId = String.valueOf(itemResp.getBody().get("id"));

        Map<String, Object> rel = Map.of(
                "name", "Order Items",
                "slug", "order-items",
                "cardinality", "one-to-many",
                "fromEntityId", orderEntityId,
                "toEntityId", itemEntityId
        );
        ResponseEntity<Map> relResp = restTemplate.exchange(
                baseUrl + "/v1/entity-relationships",
                HttpMethod.POST,
                new HttpEntity<>(rel, headers),
                Map.class
        );
        assertThat(relResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Create records
        Map<String, Object> orderValues = Map.of();
        ResponseEntity<Map> orderRecordResp = restTemplate.exchange(
                baseUrl + "/v1/tenants/" + tenantId + "/entities/" + orderEntityId + "/records",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("values", orderValues), headers),
                Map.class
        );
        String orderRecordId = String.valueOf(orderRecordResp.getBody().get("id"));

        Map<String, Object> itemValues = Map.of();
        ResponseEntity<Map> itemRecordResp = restTemplate.exchange(
                baseUrl + "/v1/tenants/" + tenantId + "/entities/" + itemEntityId + "/records",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("values", itemValues), headers),
                Map.class
        );
        String itemRecordId = String.valueOf(itemRecordResp.getBody().get("id"));

        // Create link
        Map<String, Object> linkInput = Map.of(
                "relationshipSlug", "order-items",
                "toRecordId", itemRecordId
        );
        ResponseEntity<Void> createLinkResp = restTemplate.exchange(
                baseUrl + "/v1/tenants/" + tenantId + "/records/" + orderRecordId + "/links",
                HttpMethod.POST,
                new HttpEntity<>(linkInput, headers),
                Void.class
        );
        assertThat(createLinkResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // List links
        ResponseEntity<List> listResp = restTemplate.exchange(
                baseUrl + "/v1/tenants/" + tenantId + "/records/" + orderRecordId + "/links",
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                List.class
        );
        assertThat(listResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResp.getBody()).hasSize(1);

        // Delete link
        ResponseEntity<Void> deleteResp = restTemplate.exchange(
                baseUrl + "/v1/tenants/" + tenantId + "/records/" + orderRecordId + "/links",
                HttpMethod.DELETE,
                new HttpEntity<>(linkInput, headers),
                Void.class
        );
        assertThat(deleteResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Verify deleted
        ResponseEntity<List> listAfterResp = restTemplate.exchange(
                baseUrl + "/v1/tenants/" + tenantId + "/records/" + orderRecordId + "/links",
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                List.class
        );
        assertThat(listAfterResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listAfterResp.getBody()).isEmpty();
    }
}

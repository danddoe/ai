package com.erp.entitybuilder.e2e;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EntityExtensionsE2ETest extends AbstractEntityBuilderE2ETest {

    private static final List<String> SCHEMA_PERMS = List.of(
            "entity_builder:schema:read",
            "entity_builder:schema:write"
    );

    @Test
    void endToEnd_entityExtensionsCrud() {
        Assumptions.assumeTrue(jdbcTemplate != null, "E2E skipped: no CockroachDB connection");

        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        var headers = authHeaders(userId, tenantId, SCHEMA_PERMS);

        // Create base entity
        Map<String, Object> baseEntity = new HashMap<>();
        baseEntity.put("name", "Contact");
        baseEntity.put("slug", "contact");
        baseEntity.put("status", "ACTIVE");
        baseEntity.put("categoryKey", "entity_builder");
        ResponseEntity<Map> baseResp = restTemplate.exchange(
                baseUrl + "/v1/entities",
                HttpMethod.POST,
                new HttpEntity<>(baseEntity, headers),
                Map.class
        );
        assertThat(baseResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String baseEntityId = String.valueOf(baseResp.getBody().get("id"));

        // Create extension
        Map<String, Object> create = Map.of(
                "name", "Contact Custom Fields",
                "slug", "contact-custom",
                "description", "Tenant-specific contact extension",
                "status", "ACTIVE"
        );
        ResponseEntity<Map> createResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + baseEntityId + "/extensions",
                HttpMethod.POST,
                new HttpEntity<>(create, headers),
                Map.class
        );
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String extensionId = String.valueOf(createResp.getBody().get("id"));
        assertThat(createResp.getBody().get("baseEntityId")).isEqualTo(baseEntityId);
        assertThat(createResp.getBody().get("slug")).isEqualTo("contact-custom");
        assertThat(createResp.getBody().get("categoryKey")).isEqualTo("entity_builder");

        // List extensions
        ResponseEntity<List> listResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + baseEntityId + "/extensions",
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                List.class
        );
        assertThat(listResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResp.getBody()).hasSize(1);

        // Delete extension
        ResponseEntity<Void> deleteResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + baseEntityId + "/extensions/" + extensionId,
                HttpMethod.DELETE,
                new HttpEntity<>(null, headers),
                Void.class
        );
        assertThat(deleteResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify deleted - list should be empty
        ResponseEntity<List> listAfterResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + baseEntityId + "/extensions",
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                List.class
        );
        assertThat(listAfterResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listAfterResp.getBody()).isEmpty();
    }
}

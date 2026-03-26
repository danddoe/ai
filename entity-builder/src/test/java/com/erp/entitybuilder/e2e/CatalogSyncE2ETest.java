package com.erp.entitybuilder.e2e;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogSyncE2ETest extends AbstractEntityBuilderE2ETest {

    private static final List<String> SCHEMA_PERMS = List.of(
            "entity_builder:schema:read",
            "entity_builder:schema:write"
    );

    @Test
    void catalogSync_setsStandardObjectOnSyncedEntityAndFieldsExposeScope() {
        Assumptions.assumeTrue(jdbcTemplate != null, "E2E skipped: no CockroachDB connection");

        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        var headers = authHeaders(userId, tenantId, SCHEMA_PERMS);

        ResponseEntity<Map> syncResp = restTemplate.exchange(
                baseUrl + "/v1/tenants/" + tenantId + "/catalog/sync?manifestKey=company",
                HttpMethod.POST,
                new HttpEntity<>(null, headers),
                Map.class
        );
        assertThat(syncResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> entityResp = restTemplate.exchange(
                baseUrl + "/v1/entities/by-slug/company",
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                Map.class
        );
        assertThat(entityResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(entityResp.getBody().get("slug")).isEqualTo("company");
        assertThat(entityResp.getBody().get("definitionScope")).isEqualTo("STANDARD_OBJECT");

        String entityId = String.valueOf(entityResp.getBody().get("id"));
        ResponseEntity<List> fieldsResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + entityId + "/fields",
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                List.class
        );
        assertThat(fieldsResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fieldsResp.getBody()).isNotEmpty();
        @SuppressWarnings("unchecked")
        Map<String, Object> firstField = (Map<String, Object>) fieldsResp.getBody().get(0);
        assertThat(firstField.get("definitionScope")).isEqualTo("STANDARD_OBJECT");
    }
}

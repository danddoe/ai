package com.erp.entitybuilder.e2e;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RecordsSearchVectorE2ETest extends AbstractEntityBuilderE2ETest {

    private static final List<String> RECORD_PERMS = List.of(
            "entity_builder:schema:read",
            "entity_builder:schema:write",
            "entity_builder:records:read",
            "entity_builder:records:write"
    );

    @Test
    void endToEnd_searchVectorAndLookup() {
        Assumptions.assumeTrue(jdbcTemplate != null, "E2E skipped: no CockroachDB connection");

        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        var headers = authHeaders(userId, tenantId, RECORD_PERMS);

        Map<String, Object> entityBody = Map.of("name", "Vendor", "slug", "vendor", "status", "ACTIVE");
        ResponseEntity<Map> entResp = restTemplate.exchange(
                baseUrl + "/v1/entities",
                HttpMethod.POST,
                new HttpEntity<>(entityBody, headers),
                Map.class
        );
        assertThat(entResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String entityId = String.valueOf(entResp.getBody().get("id"));

        Map<String, Object> fieldBody = Map.of(
                "name", "Company",
                "slug", "company",
                "fieldType", "string",
                "required", true,
                "pii", false,
                "config", Map.of("isSearchable", true)
        );
        restTemplate.exchange(
                baseUrl + "/v1/entities/" + entityId + "/fields",
                HttpMethod.POST,
                new HttpEntity<>(fieldBody, headers),
                Map.class
        );

        Map<String, Object> recordBody = Map.of("values", Map.of("company", "Acme Industrial Corp"));
        ResponseEntity<Map> recResp = restTemplate.exchange(
                baseUrl + "/v1/tenants/" + tenantId + "/entities/" + entityId + "/records",
                HttpMethod.POST,
                new HttpEntity<>(recordBody, headers),
                Map.class
        );
        assertThat(recResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID recordId = UUID.fromString(String.valueOf(recResp.getBody().get("id")));

        String sv = jdbcTemplate.queryForObject(
                "SELECT search_vector FROM entity_records WHERE id = ?",
                String.class,
                recordId
        );
        assertThat(sv).contains("acme");

        ResponseEntity<Map> lookupResp = restTemplate.exchange(
                baseUrl + "/v1/tenants/" + tenantId + "/entities/" + entityId + "/records/lookup?term=acm",
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                Map.class
        );
        assertThat(lookupResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) lookupResp.getBody().get("items");
        assertThat(items).hasSize(1);
        assertThat(items.get(0).get("recordId").toString()).isEqualTo(recordId.toString());
    }
}

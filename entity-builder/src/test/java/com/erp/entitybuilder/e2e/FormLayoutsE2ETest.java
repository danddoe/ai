package com.erp.entitybuilder.e2e;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FormLayoutsE2ETest extends AbstractEntityBuilderE2ETest {

    private static final List<String> SCHEMA_PERMS = List.of(
            "entity_builder:schema:read",
            "entity_builder:schema:write"
    );

    @Test
    void endToEnd_formLayoutsCrud() {
        Assumptions.assumeTrue(jdbcTemplate != null, "E2E skipped: no CockroachDB connection");

        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        var headers = authHeaders(userId, tenantId, SCHEMA_PERMS);

        // Create entity first
        Map<String, Object> createEntity = Map.of("name", "Invoice", "slug", "invoice", "status", "ACTIVE");
        ResponseEntity<Map> entityResp = restTemplate.exchange(
                baseUrl + "/v1/entities",
                HttpMethod.POST,
                new HttpEntity<>(createEntity, headers),
                Map.class
        );
        assertThat(entityResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String entityId = String.valueOf(entityResp.getBody().get("id"));

        // Create form layout
        Map<String, Object> layout = Map.of(
                "sections", List.of(
                        Map.of("title", "Basic Info", "fields", List.of("name", "date"))
                )
        );
        Map<String, Object> create = Map.of(
                "name", "Default Form",
                "layout", layout,
                "isDefault", true
        );
        ResponseEntity<Map> createResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + entityId + "/form-layouts",
                HttpMethod.POST,
                new HttpEntity<>(create, headers),
                Map.class
        );
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String layoutId = String.valueOf(createResp.getBody().get("id"));
        assertThat(createResp.getBody().get("name")).isEqualTo("Default Form");
        assertThat(createResp.getBody().get("isDefault")).isEqualTo(true);

        // List form layouts
        ResponseEntity<List> listResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + entityId + "/form-layouts",
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                List.class
        );
        assertThat(listResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResp.getBody()).hasSize(1);

        // Get form layout
        ResponseEntity<Map> getResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + entityId + "/form-layouts/" + layoutId,
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                Map.class
        );
        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResp.getBody().get("layout")).isNotNull();

        // Update form layout
        Map<String, Object> updatedLayout = Map.of(
                "sections", List.of(
                        Map.of("title", "Basic Info", "fields", List.of("name", "date", "amount"))
                )
        );
        Map<String, Object> update = Map.of("name", "Default Form Updated", "layout", updatedLayout);
        ResponseEntity<Map> updateResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + entityId + "/form-layouts/" + layoutId,
                HttpMethod.PATCH,
                new HttpEntity<>(update, headers),
                Map.class
        );
        assertThat(updateResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResp.getBody().get("name")).isEqualTo("Default Form Updated");

        // Delete form layout
        ResponseEntity<Void> deleteResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + entityId + "/form-layouts/" + layoutId,
                HttpMethod.DELETE,
                new HttpEntity<>(null, headers),
                Void.class
        );
        assertThat(deleteResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Verify deleted
        ResponseEntity<Map> getAfterResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + entityId + "/form-layouts/" + layoutId,
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                Map.class
        );
        assertThat(getAfterResp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createFormLayout_withWipStatus_persists() {
        Assumptions.assumeTrue(jdbcTemplate != null, "E2E skipped: no CockroachDB connection");

        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        var headers = authHeaders(userId, tenantId, SCHEMA_PERMS);

        Map<String, Object> createEntity = Map.of("name", "WIP Invoice", "slug", "invoice_wip_fl", "status", "ACTIVE");
        ResponseEntity<Map> entityResp = restTemplate.exchange(
                baseUrl + "/v1/entities",
                HttpMethod.POST,
                new HttpEntity<>(createEntity, headers),
                Map.class
        );
        assertThat(entityResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String entityId = String.valueOf(entityResp.getBody().get("id"));

        Map<String, Object> region = new LinkedHashMap<>();
        region.put("id", "reg-wip-test");
        region.put("role", "tab");
        region.put("title", "General");
        region.put("tabGroupId", "main");
        region.put("rows", List.of());
        Map<String, Object> layout = new LinkedHashMap<>();
        layout.put("version", 2);
        layout.put("regions", List.of(region));

        Map<String, Object> create = new LinkedHashMap<>();
        create.put("name", "Draft form");
        create.put("layout", layout);
        create.put("isDefault", false);
        create.put("status", "WIP");

        ResponseEntity<Map> createResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + entityId + "/form-layouts",
                HttpMethod.POST,
                new HttpEntity<>(create, headers),
                Map.class
        );
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(createResp.getBody().get("status")).isEqualTo("WIP");
    }
}

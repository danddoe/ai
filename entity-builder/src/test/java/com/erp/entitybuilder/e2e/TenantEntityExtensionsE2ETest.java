package com.erp.entitybuilder.e2e;

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
 * System test for tenant extensions: {@code tenant_entity_extensions} / {@code tenant_entity_extension_fields}
 * stay in sync with the public entities API (synthetic {@code entities} row + field mirror).
 */
class TenantEntityExtensionsE2ETest extends AbstractEntityBuilderE2ETest {

    private static final List<String> SCHEMA_PERMS = List.of(
            "entity_builder:schema:read",
            "entity_builder:schema:write"
    );

    @Test
    void endToEnd_tenantEntityExtensions_metadataFieldMirrorAndCascade() {
        Assumptions.assumeTrue(jdbcTemplate != null, "E2E skipped: no CockroachDB connection");

        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        var headers = authHeaders(userId, tenantId, SCHEMA_PERMS);

        Map<String, Object> baseEntity = Map.of("name", "Account", "slug", "account", "status", "ACTIVE");
        ResponseEntity<Map> baseResp = restTemplate.exchange(
                baseUrl + "/v1/entities",
                HttpMethod.POST,
                new HttpEntity<>(baseEntity, headers),
                Map.class
        );
        assertThat(baseResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        UUID baseEntityId = UUID.fromString(String.valueOf(baseResp.getBody().get("id")));

        Map<String, Object> createExt = Map.of(
                "name", "Account Extras",
                "slug", "account-extras",
                "description", "Extra columns for account",
                "status", "ACTIVE"
        );
        ResponseEntity<Map> createExtResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + baseEntityId + "/extensions",
                HttpMethod.POST,
                new HttpEntity<>(createExt, headers),
                Map.class
        );
        assertThat(createExtResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        UUID extensionEntityId = UUID.fromString(String.valueOf(createExtResp.getBody().get("id")));
        assertThat(createExtResp.getBody().get("baseEntityId")).isEqualTo(baseEntityId.toString());
        assertThat(createExtResp.getBody().get("slug")).isEqualTo("account-extras");

        UUID tenantExtId = jdbcTemplate.queryForObject(
                """
                        SELECT tenant_entity_extension_id FROM entities
                        WHERE id = ? AND tenant_id = ? AND base_entity_id = ?
                        """,
                UUID.class,
                extensionEntityId,
                tenantId,
                baseEntityId
        );
        assertThat(tenantExtId).isNotNull();

        Integer metaRows = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM tenant_entity_extensions
                        WHERE id = ? AND tenant_id = ? AND base_entity_id = ? AND slug = 'account-extras'
                        """,
                Integer.class,
                tenantExtId,
                tenantId,
                baseEntityId
        );
        assertThat(metaRows).isEqualTo(1);

        Map<String, Object> createField = Map.of(
                "name", "Loyalty tier",
                "slug", "loyalty_tier",
                "fieldType", "string",
                "required", false,
                "pii", false
        );
        ResponseEntity<Map> fieldCreateResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + extensionEntityId + "/fields",
                HttpMethod.POST,
                new HttpEntity<>(createField, headers),
                Map.class
        );
        assertThat(fieldCreateResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        UUID fieldId = UUID.fromString(String.valueOf(fieldCreateResp.getBody().get("id")));

        Integer mirrorAfterCreate = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM tenant_entity_extension_fields
                        WHERE tenant_entity_extension_id = ? AND slug = 'loyalty_tier' AND name = 'Loyalty tier'
                        """,
                Integer.class,
                tenantExtId
        );
        assertThat(mirrorAfterCreate).isEqualTo(1);

        Map<String, Object> patchField = Map.of("name", "Loyalty Tier Label", "slug", "loyalty_tier_code");
        ResponseEntity<Map> fieldPatchResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + extensionEntityId + "/fields/" + fieldId,
                HttpMethod.PATCH,
                new HttpEntity<>(patchField, headers),
                Map.class
        );
        assertThat(fieldPatchResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        Integer mirrorOldSlug = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tenant_entity_extension_fields WHERE tenant_entity_extension_id = ? AND slug = 'loyalty_tier'",
                Integer.class,
                tenantExtId
        );
        assertThat(mirrorOldSlug).isZero();

        Integer mirrorNewSlug = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM tenant_entity_extension_fields
                        WHERE tenant_entity_extension_id = ? AND slug = 'loyalty_tier_code'
                        """,
                Integer.class,
                tenantExtId
        );
        assertThat(mirrorNewSlug).isEqualTo(1);

        Map<String, Object> patchEntity = Map.of("name", "Account Extras Renamed");
        ResponseEntity<Map> entityPatchResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + extensionEntityId,
                HttpMethod.PATCH,
                new HttpEntity<>(patchEntity, headers),
                Map.class
        );
        assertThat(entityPatchResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(entityPatchResp.getBody().get("name")).isEqualTo("Account Extras Renamed");

        String metaName = jdbcTemplate.queryForObject(
                "SELECT name FROM tenant_entity_extensions WHERE id = ?",
                String.class,
                tenantExtId
        );
        assertThat(metaName).isEqualTo("Account Extras Renamed");

        ResponseEntity<Map> deleteFieldResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + extensionEntityId + "/fields/" + fieldId,
                HttpMethod.DELETE,
                new HttpEntity<>(null, headers),
                Map.class
        );
        assertThat(deleteFieldResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(deleteFieldResp.getBody().get("outcome")).isEqualTo("DELETED");

        Integer mirrorAfterFieldDelete = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tenant_entity_extension_fields WHERE tenant_entity_extension_id = ?",
                Integer.class,
                tenantExtId
        );
        assertThat(mirrorAfterFieldDelete).isZero();

        ResponseEntity<Void> deleteExtResp = restTemplate.exchange(
                baseUrl + "/v1/entities/" + baseEntityId + "/extensions/" + extensionEntityId,
                HttpMethod.DELETE,
                new HttpEntity<>(null, headers),
                Void.class
        );
        assertThat(deleteExtResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        Integer entityGone = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM entities WHERE id = ?",
                Integer.class,
                extensionEntityId
        );
        assertThat(entityGone).isZero();

        Integer metaGone = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tenant_entity_extensions WHERE id = ?",
                Integer.class,
                tenantExtId
        );
        assertThat(metaGone).isZero();

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

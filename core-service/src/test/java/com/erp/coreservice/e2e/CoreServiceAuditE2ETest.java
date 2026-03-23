package com.erp.coreservice.e2e;

import com.erp.audit.AuditOperations;
import com.erp.audit.AuditableResourceSupport;
import com.erp.coreservice.domain.Company;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end verification that core master-data mutations append {@code audit_log} rows
 * (same transaction / datasource as core-service Flyway).
 */
class CoreServiceAuditE2ETest extends AbstractCoreServiceE2ETest {

    @Test
    void endToEnd_companyCreateAndPatchWritesAuditLog() {
        Assumptions.assumeTrue(jdbcTemplate != null, "E2E skipped: no CockroachDB connection");

        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        var headers = authHeaders(userId, tenantId, List.of(
                "master_data:companies:read",
                "master_data:companies:write"
        ));

        String expectedCreateAction = AuditableResourceSupport.action(Company.class, "create");
        String expectedUpdateAction = AuditableResourceSupport.action(Company.class, "update");
        String expectedResourceType = AuditableResourceSupport.resourceType(Company.class);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*)::bigint FROM audit_log WHERE tenant_id = ?",
                Long.class,
                tenantId
        )).isZero();

        Map<String, Object> body = new HashMap<>();
        body.put("companyName", "E2E Audit Corp");
        body.put("baseCurrency", "USD");
        ResponseEntity<Map> createResp = restTemplate.exchange(
                tenantPath(tenantId, "/companies"),
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class
        );
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        UUID companyId = UUID.fromString(String.valueOf(createResp.getBody().get("companyId")));

        List<Map<String, Object>> createRows = jdbcTemplate.queryForList(
                """
                        SELECT action, resource_type, operation, source_service, actor_id, resource_id,
                               payload::text AS payload_text
                        FROM audit_log
                        WHERE tenant_id = ? AND resource_id = ?
                        ORDER BY created_at
                        """,
                tenantId,
                companyId
        );
        assertThat(createRows).hasSize(1);
        Map<String, Object> createRow = createRows.get(0);
        assertThat(createRow.get("action")).isEqualTo(expectedCreateAction);
        assertThat(createRow.get("resource_type")).isEqualTo(expectedResourceType);
        assertThat(createRow.get("operation")).isEqualTo(AuditOperations.CREATE);
        assertThat(createRow.get("source_service")).isEqualTo("core-service");
        assertThat(createRow.get("actor_id").toString()).isEqualTo(userId.toString());
        assertThat(createRow.get("resource_id").toString()).isEqualTo(companyId.toString());
        assertThat((String) createRow.get("payload_text"))
                .contains("E2E Audit Corp")
                .contains("\"after\"");

        Map<String, Object> patch = new HashMap<>();
        patch.put("companyName", "E2E Audit Renamed");
        patch.put("alias", "audit-alias");
        ResponseEntity<Map> patchResp = restTemplate.exchange(
                tenantPath(tenantId, "/companies/" + companyId),
                HttpMethod.PATCH,
                new HttpEntity<>(patch, headers),
                Map.class
        );
        assertThat(patchResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<Map<String, Object>> allRows = jdbcTemplate.queryForList(
                """
                        SELECT action, operation, payload::text AS payload_text
                        FROM audit_log
                        WHERE tenant_id = ? AND resource_id = ?
                        ORDER BY created_at
                        """,
                tenantId,
                companyId
        );
        assertThat(allRows).hasSize(2);

        Map<String, Object> updateRow = allRows.get(1);
        assertThat(updateRow.get("action")).isEqualTo(expectedUpdateAction);
        assertThat(updateRow.get("operation")).isEqualTo(AuditOperations.UPDATE);
        String updatePayload = (String) updateRow.get("payload_text");
        assertThat(updatePayload)
                .contains("changes")
                .contains("row.companyName")
                .contains("E2E Audit Renamed")
                .contains("row.alias")
                .contains("audit-alias");
    }
}

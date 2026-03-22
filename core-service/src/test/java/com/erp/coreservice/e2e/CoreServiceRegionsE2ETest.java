package com.erp.coreservice.e2e;

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

class CoreServiceRegionsE2ETest extends AbstractCoreServiceE2ETest {

    @Test
    void endToEnd_regionsCrud() {
        Assumptions.assumeTrue(jdbcTemplate != null, "E2E skipped: no CockroachDB connection");

        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        var headers = authHeaders(userId, tenantId, List.of(
                "master_data:regions:read",
                "master_data:regions:write"
        ));

        Map<String, Object> create = new HashMap<>();
        create.put("regionCode", "E2E-AMER");
        create.put("regionName", "E2E Americas");
        ResponseEntity<Map> createResp = restTemplate.exchange(
                tenantPath(tenantId, "/regions"),
                HttpMethod.POST,
                new HttpEntity<>(create, headers),
                Map.class
        );
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        UUID regionId = UUID.fromString(String.valueOf(createResp.getBody().get("regionId")));

        ResponseEntity<Map> getResp = restTemplate.exchange(
                tenantPath(tenantId, "/regions/" + regionId),
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                Map.class
        );
        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResp.getBody().get("regionCode")).isEqualTo("E2E-AMER");

        Map<String, Object> patch = new HashMap<>();
        patch.put("regionName", "E2E Americas Updated");
        ResponseEntity<Map> patchResp = restTemplate.exchange(
                tenantPath(tenantId, "/regions/" + regionId),
                HttpMethod.PATCH,
                new HttpEntity<>(patch, headers),
                Map.class
        );
        assertThat(patchResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(patchResp.getBody().get("regionName")).isEqualTo("E2E Americas Updated");
    }
}

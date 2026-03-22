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

class CoreServiceBusinessUnitsE2ETest extends AbstractCoreServiceE2ETest {

    @Test
    void endToEnd_businessUnitsUnderCompany() {
        Assumptions.assumeTrue(jdbcTemplate != null, "E2E skipped: no CockroachDB connection");

        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        var headers = authHeaders(userId, tenantId, List.of(
                "master_data:companies:read",
                "master_data:companies:write",
                "master_data:business_units:read",
                "master_data:business_units:write"
        ));

        Map<String, Object> company = new HashMap<>();
        company.put("companyName", "E2E BU Parent Co");
        company.put("baseCurrency", "USD");
        ResponseEntity<Map> coResp = restTemplate.exchange(
                tenantPath(tenantId, "/companies"),
                HttpMethod.POST,
                new HttpEntity<>(company, headers),
                Map.class
        );
        assertThat(coResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        UUID companyId = UUID.fromString(String.valueOf(coResp.getBody().get("companyId")));

        Map<String, Object> bu = new HashMap<>();
        bu.put("companyId", companyId.toString());
        bu.put("buType", "Division");
        bu.put("buName", "E2E North Sales");
        ResponseEntity<Map> buResp = restTemplate.exchange(
                tenantPath(tenantId, "/business-units"),
                HttpMethod.POST,
                new HttpEntity<>(bu, headers),
                Map.class
        );
        assertThat(buResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        UUID buId = UUID.fromString(String.valueOf(buResp.getBody().get("buId")));

        ResponseEntity<Map> listResp = restTemplate.exchange(
                tenantPath(tenantId, "/business-units?companyId=" + companyId + "&page=1&pageSize=20"),
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                Map.class
        );
        assertThat(listResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResp.getBody().get("total")).isEqualTo(1);

        Map<String, Object> patch = new HashMap<>();
        patch.put("buName", "E2E North Sales Renamed");
        ResponseEntity<Map> patchResp = restTemplate.exchange(
                tenantPath(tenantId, "/business-units/" + buId),
                HttpMethod.PATCH,
                new HttpEntity<>(patch, headers),
                Map.class
        );
        assertThat(patchResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(patchResp.getBody().get("buName")).isEqualTo("E2E North Sales Renamed");
    }
}

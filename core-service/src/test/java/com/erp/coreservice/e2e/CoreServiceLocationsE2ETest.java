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

class CoreServiceLocationsE2ETest extends AbstractCoreServiceE2ETest {

    @Test
    void endToEnd_locationsWithCountryAndRegion() {
        Assumptions.assumeTrue(jdbcTemplate != null, "E2E skipped: no CockroachDB connection");

        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        var headers = authHeaders(userId, tenantId, List.of(
                "master_data:regions:read",
                "master_data:regions:write",
                "master_data:locations:read",
                "master_data:locations:write"
        ));

        Map<String, Object> region = new HashMap<>();
        region.put("regionCode", "E2E-LOC-R");
        region.put("regionName", "E2E Loc Region");
        ResponseEntity<Map> regResp = restTemplate.exchange(
                tenantPath(tenantId, "/regions"),
                HttpMethod.POST,
                new HttpEntity<>(region, headers),
                Map.class
        );
        assertThat(regResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        UUID regionId = UUID.fromString(String.valueOf(regResp.getBody().get("regionId")));

        Map<String, Object> loc = new HashMap<>();
        loc.put("locationName", "E2E HQ");
        loc.put("addressLine1", "1 Test Way");
        loc.put("city", "Austin");
        loc.put("stateProvince", "TX");
        loc.put("postalCode", "78701");
        loc.put("countryCode", "US");
        loc.put("regionId", regionId.toString());
        ResponseEntity<Map> locResp = restTemplate.exchange(
                tenantPath(tenantId, "/locations"),
                HttpMethod.POST,
                new HttpEntity<>(loc, headers),
                Map.class
        );
        assertThat(locResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        UUID locationId = UUID.fromString(String.valueOf(locResp.getBody().get("locationId")));
        assertThat(locResp.getBody().get("countryCode")).isEqualTo("US");

        Map<String, Object> patch = new HashMap<>();
        patch.put("city", "Dallas");
        ResponseEntity<Map> patchResp = restTemplate.exchange(
                tenantPath(tenantId, "/locations/" + locationId),
                HttpMethod.PATCH,
                new HttpEntity<>(patch, headers),
                Map.class
        );
        assertThat(patchResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(patchResp.getBody().get("city")).isEqualTo("Dallas");
    }
}

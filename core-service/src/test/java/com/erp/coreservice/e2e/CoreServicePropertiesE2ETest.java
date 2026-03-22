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

class CoreServicePropertiesE2ETest extends AbstractCoreServiceE2ETest {

    @Test
    void endToEnd_propertiesLinkCompanyAndLocation() {
        Assumptions.assumeTrue(jdbcTemplate != null, "E2E skipped: no CockroachDB connection");

        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        var headers = authHeaders(userId, tenantId, List.of(
                "master_data:companies:read",
                "master_data:companies:write",
                "master_data:locations:read",
                "master_data:locations:write",
                "master_data:properties:read",
                "master_data:properties:write"
        ));

        Map<String, Object> company = new HashMap<>();
        company.put("companyName", "E2E Prop Owner");
        company.put("baseCurrency", "USD");
        ResponseEntity<Map> coResp = restTemplate.exchange(
                tenantPath(tenantId, "/companies"),
                HttpMethod.POST,
                new HttpEntity<>(company, headers),
                Map.class
        );
        UUID companyId = UUID.fromString(String.valueOf(coResp.getBody().get("companyId")));

        Map<String, Object> loc = new HashMap<>();
        loc.put("locationName", "E2E Prop Site");
        loc.put("countryCode", "US");
        ResponseEntity<Map> locResp = restTemplate.exchange(
                tenantPath(tenantId, "/locations"),
                HttpMethod.POST,
                new HttpEntity<>(loc, headers),
                Map.class
        );
        UUID locationId = UUID.fromString(String.valueOf(locResp.getBody().get("locationId")));

        Map<String, Object> prop = new HashMap<>();
        prop.put("companyId", companyId.toString());
        prop.put("locationId", locationId.toString());
        prop.put("propertyName", "E2E Sunset Plaza");
        prop.put("propertyType", "Commercial Retail");
        ResponseEntity<Map> propResp = restTemplate.exchange(
                tenantPath(tenantId, "/properties"),
                HttpMethod.POST,
                new HttpEntity<>(prop, headers),
                Map.class
        );
        assertThat(propResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        UUID propertyId = UUID.fromString(String.valueOf(propResp.getBody().get("propertyId")));

        Map<String, Object> patch = new HashMap<>();
        patch.put("propertyName", "E2E Sunset Plaza II");
        ResponseEntity<Map> patchResp = restTemplate.exchange(
                tenantPath(tenantId, "/properties/" + propertyId),
                HttpMethod.PATCH,
                new HttpEntity<>(patch, headers),
                Map.class
        );
        assertThat(patchResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(patchResp.getBody().get("propertyName")).isEqualTo("E2E Sunset Plaza II");
    }
}

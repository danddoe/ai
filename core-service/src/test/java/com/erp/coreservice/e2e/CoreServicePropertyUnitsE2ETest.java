package com.erp.coreservice.e2e;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CoreServicePropertyUnitsE2ETest extends AbstractCoreServiceE2ETest {

    @Test
    void endToEnd_propertyUnitsOnProperty() {
        Assumptions.assumeTrue(jdbcTemplate != null, "E2E skipped: no CockroachDB connection");

        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        var headers = authHeaders(userId, tenantId, ALL_MASTER_DATA_PERMS);

        Map<String, Object> company = new HashMap<>();
        company.put("companyName", "E2E Unit Owner");
        company.put("baseCurrency", "USD");
        ResponseEntity<Map> coResp = restTemplate.exchange(
                tenantPath(tenantId, "/companies"),
                HttpMethod.POST,
                new HttpEntity<>(company, headers),
                Map.class
        );
        UUID companyId = UUID.fromString(String.valueOf(coResp.getBody().get("companyId")));

        Map<String, Object> loc = new HashMap<>();
        loc.put("locationName", "E2E Unit Addr");
        loc.put("countryCode", "DE");
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
        prop.put("propertyName", "E2E Berlin Bldg");
        prop.put("propertyType", "Multi-Family");
        ResponseEntity<Map> propResp = restTemplate.exchange(
                tenantPath(tenantId, "/properties"),
                HttpMethod.POST,
                new HttpEntity<>(prop, headers),
                Map.class
        );
        UUID propertyId = UUID.fromString(String.valueOf(propResp.getBody().get("propertyId")));

        Map<String, Object> unit = new HashMap<>();
        unit.put("propertyId", propertyId.toString());
        unit.put("unitNumber", "101");
        unit.put("squareFootage", new BigDecimal("850.5"));
        unit.put("status", "Vacant");
        ResponseEntity<Map> unitResp = restTemplate.exchange(
                tenantPath(tenantId, "/property-units"),
                HttpMethod.POST,
                new HttpEntity<>(unit, headers),
                Map.class
        );
        assertThat(unitResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        UUID unitId = UUID.fromString(String.valueOf(unitResp.getBody().get("unitId")));

        ResponseEntity<Map> listResp = restTemplate.exchange(
                tenantPath(tenantId, "/property-units?propertyId=" + propertyId + "&page=1&pageSize=10"),
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                Map.class
        );
        assertThat(listResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResp.getBody().get("total")).isEqualTo(1);

        Map<String, Object> patch = new HashMap<>();
        patch.put("status", "Leased");
        ResponseEntity<Map> patchResp = restTemplate.exchange(
                tenantPath(tenantId, "/property-units/" + unitId),
                HttpMethod.PATCH,
                new HttpEntity<>(patch, headers),
                Map.class
        );
        assertThat(patchResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(patchResp.getBody().get("status")).isEqualTo("Leased");
    }
}

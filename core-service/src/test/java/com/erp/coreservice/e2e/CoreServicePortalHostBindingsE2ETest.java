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

class CoreServicePortalHostBindingsE2ETest extends AbstractCoreServiceE2ETest {

    @Test
    void endToEnd_portalHostBindings_companyScopeAndBootstrap() {
        Assumptions.assumeTrue(jdbcTemplate != null, "E2E skipped: no CockroachDB connection");

        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        var headers = authHeaders(userId, tenantId, List.of(
                "master_data:companies:read",
                "master_data:companies:write",
                "master_data:business_units:read",
                "master_data:business_units:write"
        ));

        Map<String, Object> companyBody = new HashMap<>();
        companyBody.put("companyName", "Portal Binding Corp");
        companyBody.put("baseCurrency", "USD");
        ResponseEntity<Map> companyResp = restTemplate.exchange(
                tenantPath(tenantId, "/companies"),
                HttpMethod.POST,
                new HttpEntity<>(companyBody, headers),
                Map.class
        );
        assertThat(companyResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        UUID companyId = UUID.fromString(String.valueOf(companyResp.getBody().get("companyId")));

        Map<String, Object> buBody = new HashMap<>();
        buBody.put("companyId", companyId.toString());
        buBody.put("buType", "BRANCH");
        buBody.put("buName", "Default Branch");
        ResponseEntity<Map> buResp = restTemplate.exchange(
                tenantPath(tenantId, "/business-units"),
                HttpMethod.POST,
                new HttpEntity<>(buBody, headers),
                Map.class
        );
        assertThat(buResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        UUID buId = UUID.fromString(String.valueOf(buResp.getBody().get("buId")));

        Map<String, Object> patchCompany = new HashMap<>();
        patchCompany.put("defaultPortalBuId", buId.toString());
        ResponseEntity<Map> patchCo = restTemplate.exchange(
                tenantPath(tenantId, "/companies/" + companyId),
                HttpMethod.PATCH,
                new HttpEntity<>(patchCompany, headers),
                Map.class
        );
        assertThat(patchCo.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(patchCo.getBody().get("defaultPortalBuId")).isEqualTo(buId.toString());

        String hostname = "e2e-portal-binding.example";
        Map<String, Object> bindBody = new HashMap<>();
        bindBody.put("hostname", hostname);
        bindBody.put("scope", "COMPANY");
        bindBody.put("companyId", companyId.toString());
        ResponseEntity<Map> bindResp = restTemplate.exchange(
                tenantPath(tenantId, "/portal-host-bindings"),
                HttpMethod.POST,
                new HttpEntity<>(bindBody, headers),
                Map.class
        );
        assertThat(bindResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bindResp.getBody().get("hostname")).isEqualTo(hostname);
        assertThat(bindResp.getBody().get("scope")).isEqualTo("COMPANY");

        ResponseEntity<Map> boot = restTemplate.getForEntity(
                baseUrl + "/v1/portal/bootstrap?hostname=" + hostname,
                Map.class
        );
        assertThat(boot.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(boot.getBody().get("tenantId")).isEqualTo(tenantId.toString());
        assertThat(boot.getBody().get("companyId")).isEqualTo(companyId.toString());
        assertThat(boot.getBody().get("defaultBuId")).isEqualTo(buId.toString());
        assertThat(boot.getBody().get("scope")).isEqualTo("COMPANY");
    }
}

package com.erp.coreservice.e2e;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CoreServiceCompaniesE2ETest extends AbstractCoreServiceE2ETest {

    @Test
    void endToEnd_companiesCrudAndHierarchy() {
        Assumptions.assumeTrue(jdbcTemplate != null, "E2E skipped: no CockroachDB connection");

        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        var headers = authHeaders(userId, tenantId, List.of(
                "master_data:companies:read",
                "master_data:companies:write"
        ));

        Map<String, Object> root = new HashMap<>();
        root.put("companyName", "E2E Root Corp");
        root.put("baseCurrency", "USD");
        ResponseEntity<Map> rootResp = restTemplate.exchange(
                tenantPath(tenantId, "/companies"),
                HttpMethod.POST,
                new HttpEntity<>(root, headers),
                Map.class
        );
        assertThat(rootResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        UUID rootId = UUID.fromString(String.valueOf(rootResp.getBody().get("companyId")));
        assertThat(rootResp.getBody().get("companyName")).isEqualTo("E2E Root Corp");

        Map<String, Object> child = new HashMap<>();
        child.put("companyName", "E2E Sub Inc");
        child.put("baseCurrency", "EUR");
        child.put("parentCompanyId", rootId.toString());
        child.put("ownershipPct", new BigDecimal("100.00"));
        ResponseEntity<Map> childResp = restTemplate.exchange(
                tenantPath(tenantId, "/companies"),
                HttpMethod.POST,
                new HttpEntity<>(child, headers),
                Map.class
        );
        assertThat(childResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        UUID childId = UUID.fromString(String.valueOf(childResp.getBody().get("companyId")));
        assertThat(childResp.getBody().get("parentCompanyId")).isEqualTo(rootId.toString());

        ResponseEntity<Map> pageResp = restTemplate.exchange(
                tenantPath(tenantId, "/companies?page=1&pageSize=10"),
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                Map.class
        );
        assertThat(pageResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pageResp.getBody().get("total")).isEqualTo(2);

        Map<String, Object> patch = new HashMap<>();
        patch.put("companyName", "E2E Sub Renamed");
        ResponseEntity<Map> patchResp = restTemplate.exchange(
                tenantPath(tenantId, "/companies/" + childId),
                HttpMethod.PATCH,
                new HttpEntity<>(patch, headers),
                Map.class
        );
        assertThat(patchResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(patchResp.getBody().get("companyName")).isEqualTo("E2E Sub Renamed");

        Map<String, Object> patchAlias = new HashMap<>();
        patchAlias.put("alias", "E2E Sub Alias");
        ResponseEntity<Map> patchAliasResp = restTemplate.exchange(
                tenantPath(tenantId, "/companies/" + childId),
                HttpMethod.PATCH,
                new HttpEntity<>(patchAlias, headers),
                Map.class
        );
        assertThat(patchAliasResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(patchAliasResp.getBody().get("alias")).isEqualTo("E2E Sub Alias");

        ResponseEntity<Map> getAfterAlias = restTemplate.exchange(
                tenantPath(tenantId, "/companies/" + childId),
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                Map.class
        );
        assertThat(getAfterAlias.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getAfterAlias.getBody().get("alias")).isEqualTo("E2E Sub Alias");

        Map<String, Object> clearParent = new HashMap<>();
        clearParent.put("clearParentCompany", true);
        ResponseEntity<Map> clearResp = restTemplate.exchange(
                tenantPath(tenantId, "/companies/" + childId),
                HttpMethod.PATCH,
                new HttpEntity<>(clearParent, headers),
                Map.class
        );
        assertThat(clearResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(clearResp.getBody().get("parentCompanyId")).isNull();
    }
}

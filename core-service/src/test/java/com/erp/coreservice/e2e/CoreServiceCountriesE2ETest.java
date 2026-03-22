package com.erp.coreservice.e2e;

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

class CoreServiceCountriesE2ETest extends AbstractCoreServiceE2ETest {

    @Test
    void endToEnd_listCountries_seededIsoRows() {
        Assumptions.assumeTrue(jdbcTemplate != null, "E2E skipped: no CockroachDB connection");

        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        var headers = authHeaders(userId, tenantId, List.of("master_data:countries:read"));

        ResponseEntity<List> resp = restTemplate.exchange(
                baseUrl + "/v1/countries",
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                List.class
        );
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotEmpty();
        boolean hasUs = false;
        for (Object o : resp.getBody()) {
            Map<?, ?> m = (Map<?, ?>) o;
            if ("US".equals(String.valueOf(m.get("countryCode")))) {
                hasUs = true;
                break;
            }
        }
        assertThat(hasUs).isTrue();
    }
}

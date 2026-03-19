package com.erp.entitybuilder.e2e;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiGuideE2ETest extends AbstractEntityBuilderE2ETest {

    @Test
    void endToEnd_aiGuidePublicEndpoint() {
        Assumptions.assumeTrue(jdbcTemplate != null, "E2E skipped: no CockroachDB connection");

        // Public endpoint - no auth required
        ResponseEntity<Map> resp = restTemplate.getForEntity(baseUrl + "/v1/ai/guide", Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).containsKey("service");
        assertThat(resp.getBody().get("service")).isEqualTo("entity-builder");
        assertThat(resp.getBody()).containsKey("workflows");
        assertThat(resp.getBody()).containsKey("auth");
        assertThat(resp.getBody()).containsKey("docs");
    }
}

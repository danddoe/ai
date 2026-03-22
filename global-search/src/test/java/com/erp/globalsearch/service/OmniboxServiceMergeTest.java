package com.erp.globalsearch.service;

import com.erp.globalsearch.config.GlobalSearchProperties;
import com.erp.globalsearch.web.dto.OmniboxDtos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Verifies scatter-gather merge when upstream JSON parses; uses stub WebClients.
 */
@ExtendWith(MockitoExtension.class)
class OmniboxServiceMergeTest {

    @Mock
    DeepSearchClient deepSearchClient;

    @Test
    void mergesNavigationAndRecords() {
        UUID tenantId = UUID.randomUUID();

        WebClient iam = WebClient.builder()
                .baseUrl("http://localhost")
                .exchangeFunction(req -> {
                    String json = """
                            {"items":[{"id":"550e8400-e29b-41d4-a716-446655440000","label":"Home","description":null,"routePath":"/home","type":"internal","icon":null,"categoryKey":"general"}]}
                            """.replace("\n", "");
                    return Mono.just(org.springframework.web.reactive.function.client.ClientResponse.create(org.springframework.http.HttpStatus.OK)
                            .header("Content-Type", "application/json")
                            .body(json)
                            .build());
                })
                .build();

        WebClient eb = WebClient.builder()
                .baseUrl("http://localhost")
                .exchangeFunction(req -> {
                    String json = """
                            {"items":[{"sourceRecordId":"660e8400-e29b-41d4-a716-446655440001","sourceEntityId":"770e8400-e29b-41d4-a716-446655440002","title":"Row","subtitle":"Ent","routePath":"/e/r"}]}
                            """.replace("\n", "");
                    return Mono.just(org.springframework.web.reactive.function.client.ClientResponse.create(org.springframework.http.HttpStatus.OK)
                            .header("Content-Type", "application/json")
                            .body(json)
                            .build());
                })
                .build();

        GlobalSearchProperties props = new GlobalSearchProperties();
        props.setUpstreamTimeoutMs(5000);
        props.setDefaultLimitPerCategory(12);

        when(deepSearchClient.search(anyString(), anyInt(), eq(tenantId))).thenReturn(List.of());

        OmniboxService svc = new OmniboxService(iam, eb, props, deepSearchClient);
        OmniboxDtos.OmniboxResponse r = svc.search("ho", 10, "Bearer t", tenantId);

        assertThat(r.navigation()).hasSize(1);
        assertThat(r.navigation().get(0).title()).isEqualTo("Home");
        assertThat(r.records()).hasSize(1);
        assertThat(r.records().get(0).title()).isEqualTo("Row");
        assertThat(r.deepHistory()).isEmpty();
    }

    @Test
    void recordsEmptyOnForbidden() {
        UUID tenantId = UUID.randomUUID();

        WebClient iam = WebClient.builder()
                .baseUrl("http://localhost")
                .exchangeFunction(req -> Mono.just(org.springframework.web.reactive.function.client.ClientResponse.create(org.springframework.http.HttpStatus.OK)
                        .header("Content-Type", "application/json")
                        .body("{\"items\":[]}")
                        .build()))
                .build();

        WebClient eb = WebClient.builder()
                .baseUrl("http://localhost")
                .exchangeFunction(req -> Mono.error(WebClientResponseException.create(403, "Forbidden", null, null, null)))
                .build();

        GlobalSearchProperties props = new GlobalSearchProperties();
        props.setUpstreamTimeoutMs(5000);

        when(deepSearchClient.search(anyString(), anyInt(), eq(tenantId))).thenReturn(List.of());

        OmniboxService svc = new OmniboxService(iam, eb, props, deepSearchClient);
        OmniboxDtos.OmniboxResponse r = svc.search("ab", 10, "Bearer t", tenantId);

        assertThat(r.records()).isEmpty();
    }
}

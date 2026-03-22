package com.erp.globalsearch.service;

import com.erp.globalsearch.config.GlobalSearchProperties;
import com.erp.globalsearch.web.dto.OmniboxDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OmniboxService {

    private static final Logger log = LoggerFactory.getLogger(OmniboxService.class);

    private final WebClient iamWebClient;
    private final WebClient entityBuilderWebClient;
    private final GlobalSearchProperties props;
    private final DeepSearchClient deepSearchClient;

    public OmniboxService(
            @Qualifier("iamWebClient") WebClient iamWebClient,
            @Qualifier("entityBuilderWebClient") WebClient entityBuilderWebClient,
            GlobalSearchProperties props,
            DeepSearchClient deepSearchClient
    ) {
        this.iamWebClient = iamWebClient;
        this.entityBuilderWebClient = entityBuilderWebClient;
        this.props = props;
        this.deepSearchClient = deepSearchClient;
    }

    public OmniboxDtos.OmniboxResponse search(String q, Integer limitPerCategory, String authorizationHeader, UUID tenantId) {
        try {
            return searchInternal(q, limitPerCategory, authorizationHeader, tenantId);
        } catch (Throwable t) {
            log.error("Omnibox search failed for tenant {}: {}", tenantId, t.toString(), t);
            return new OmniboxDtos.OmniboxResponse(List.of(), List.of(), List.of());
        }
    }

    private OmniboxDtos.OmniboxResponse searchInternal(
            String q, Integer limitPerCategory, String authorizationHeader, UUID tenantId) {
        int limit = limitPerCategory != null && limitPerCategory > 0
                ? Math.min(limitPerCategory, 50)
                : props.getDefaultLimitPerCategory();

        Duration timeout = Duration.ofMillis(props.getUpstreamTimeoutMs());

        Mono<OmniboxDtos.NavigationSearchResponse> navMono = iamWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1/navigation/search")
                        .queryParam("q", q)
                        .queryParam("limit", limit)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                .retrieve()
                .bodyToMono(OmniboxDtos.NavigationSearchResponse.class)
                .timeout(timeout)
                .onErrorResume(ex -> Mono.just(new OmniboxDtos.NavigationSearchResponse(List.of())));

        Mono<OmniboxDtos.GlobalRecordSearchResponse> recordsMono = entityBuilderWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1/tenants/{tenantId}/search/records")
                        .queryParam("q", q)
                        .queryParam("limit", limit)
                        .build(tenantId))
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                .retrieve()
                .bodyToMono(OmniboxDtos.GlobalRecordSearchResponse.class)
                .timeout(timeout)
                .onErrorResume(WebClientResponseException.Forbidden.class, ex -> Mono.just(new OmniboxDtos.GlobalRecordSearchResponse(List.of())))
                .onErrorResume(WebClientResponseException.Unauthorized.class, ex -> Mono.just(new OmniboxDtos.GlobalRecordSearchResponse(List.of())))
                .onErrorResume(ex -> Mono.just(new OmniboxDtos.GlobalRecordSearchResponse(List.of())));

        List<OmniboxDtos.OmniboxItem> deep;
        try {
            deep = deepSearchClient.search(q, limit, tenantId);
        } catch (RuntimeException e) {
            log.warn("Deep search client failed for tenant {}: {}", tenantId, e.toString());
            deep = List.of();
        }
        try {
            var zipped = Mono.zip(navMono, recordsMono).block(timeout.plus(Duration.ofMillis(500)));
            if (zipped == null) {
                return new OmniboxDtos.OmniboxResponse(List.of(), List.of(), deep);
            }
            OmniboxDtos.NavigationSearchResponse nav = zipped.getT1();
            OmniboxDtos.GlobalRecordSearchResponse rec = zipped.getT2();

            List<OmniboxDtos.OmniboxItem> navItems = mapNavigation(nav != null ? nav.items() : null);
            List<OmniboxDtos.OmniboxItem> recordItems = mapRecords(rec != null ? rec.items() : null);

            return new OmniboxDtos.OmniboxResponse(navItems, recordItems, deep);
        } catch (RuntimeException e) {
            log.warn("Omnibox upstream merge failed for tenant {}: {}", tenantId, e.toString());
            return new OmniboxDtos.OmniboxResponse(List.of(), List.of(), deep);
        }
    }

    private List<OmniboxDtos.OmniboxItem> mapNavigation(List<OmniboxDtos.NavigationSearchHitDto> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<OmniboxDtos.OmniboxItem> out = new ArrayList<>();
        for (OmniboxDtos.NavigationSearchHitDto h : items) {
            if (h == null || h.routePath() == null || h.routePath().isBlank()) {
                continue;
            }
            String navKey = h.id() != null ? h.id().toString() : "x";
            Map<String, Object> meta = new HashMap<>();
            meta.put("navigationItemId", h.id() != null ? h.id().toString() : null);
            meta.put("type", h.type());
            out.add(new OmniboxDtos.OmniboxItem(
                    "nav-" + navKey,
                    "navigation",
                    h.label(),
                    h.description() != null ? h.description() : "",
                    h.routePath(),
                    h.icon(),
                    meta
            ));
        }
        return out;
    }

    private List<OmniboxDtos.OmniboxItem> mapRecords(List<OmniboxDtos.GlobalRecordSearchItemDto> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<OmniboxDtos.OmniboxItem> out = new ArrayList<>();
        for (OmniboxDtos.GlobalRecordSearchItemDto h : items) {
            if (h == null) {
                continue;
            }
            UUID sid = h.sourceRecordId();
            String recKey = sid != null ? sid.toString() : "unknown";
            Map<String, Object> meta = new HashMap<>();
            meta.put("sourceEntityId", h.sourceEntityId() != null ? h.sourceEntityId().toString() : null);
            meta.put("sourceRecordId", sid != null ? sid.toString() : null);
            out.add(new OmniboxDtos.OmniboxItem(
                    "rec-" + recKey,
                    "records",
                    h.title() != null ? h.title() : recKey,
                    h.subtitle() != null ? h.subtitle() : "",
                    h.routePath() != null ? h.routePath() : "",
                    "document",
                    meta
            ));
        }
        return out;
    }
}

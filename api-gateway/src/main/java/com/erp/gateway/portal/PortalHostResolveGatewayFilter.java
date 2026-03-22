package com.erp.gateway.portal;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Strips client-supplied resolved-host headers, resolves {@code Host} via core-service bootstrap API,
 * and forwards trusted {@code X-Resolved-*} when {@code gateway.portal-host.trust-secret} is set.
 */
@Component
public class PortalHostResolveGatewayFilter implements GlobalFilter, Ordered {

    private static final String HDR_TRUST = "X-Gateway-Trusted";
    private static final String HDR_TENANT = "X-Resolved-Tenant-Id";
    private static final String HDR_COMPANY = "X-Resolved-Company-Id";
    private static final String HDR_BU = "X-Resolved-Default-Bu-Id";
    private static final String FWD_HOST = "X-Forwarded-Host";

    private final PortalHostResolveProperties properties;
    private final WebClient webClient;
    private final LoadingCache<String, Optional<PortalBootstrapJson>> cache;

    public PortalHostResolveGatewayFilter(
            PortalHostResolveProperties properties,
            WebClient.Builder webClientBuilder
    ) {
        this.properties = properties;
        this.webClient = webClientBuilder.build();
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(Math.max(1, properties.getCacheTtlSeconds())))
                .build(this::loadBootstrap);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String secret = properties.getTrustSecret();
        if (secret == null || secret.isEmpty()) {
            return chain.filter(exchange);
        }

        String host = effectiveHost(exchange);
        String norm = PortalHostnameNormalizer.normalize(host);
        if (norm == null) {
            return chain.filter(stripResolved(exchange));
        }

        Optional<PortalBootstrapJson> resolved = cache.get(norm);
        ServerWebExchange next = stripResolved(exchange);
        if (resolved.isEmpty() || resolved.get().getTenantId() == null) {
            return chain.filter(next);
        }
        PortalBootstrapJson b = resolved.get();
        ServerHttpRequest req = next.getRequest().mutate()
                .headers(h -> {
                    h.set(HDR_TRUST, secret);
                    h.set(HDR_TENANT, b.getTenantId().toString());
                    if (b.getCompanyId() != null) {
                        h.set(HDR_COMPANY, b.getCompanyId().toString());
                    } else {
                        h.remove(HDR_COMPANY);
                    }
                    if (b.getDefaultBuId() != null) {
                        h.set(HDR_BU, b.getDefaultBuId().toString());
                    } else {
                        h.remove(HDR_BU);
                    }
                })
                .build();
        return chain.filter(next.mutate().request(req).build());
    }

    private static ServerWebExchange stripResolved(ServerWebExchange exchange) {
        ServerHttpRequest req = exchange.getRequest().mutate()
                .headers(h -> {
                    h.remove(HDR_TENANT);
                    h.remove(HDR_COMPANY);
                    h.remove(HDR_BU);
                    h.remove(HDR_TRUST);
                })
                .build();
        return exchange.mutate().request(req).build();
    }

    private String effectiveHost(ServerWebExchange exchange) {
        List<String> forwarded = exchange.getRequest().getHeaders().get(FWD_HOST);
        if (forwarded != null && !forwarded.isEmpty()) {
            String first = forwarded.get(0);
            if (first != null && !first.isBlank()) {
                return first.split(",")[0].trim();
            }
        }
        return exchange.getRequest().getURI().getHost();
    }

    private Optional<PortalBootstrapJson> loadBootstrap(String normalizedHostKey) {
        try {
            URI uri = UriComponentsBuilder.fromUriString(properties.getCoreServiceUrl())
                    .path("/v1/portal/bootstrap")
                    .queryParam("hostname", normalizedHostKey)
                    .build(true)
                    .toUri();
            PortalBootstrapJson body = webClient.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(PortalBootstrapJson.class)
                    .block(Duration.ofSeconds(5));
            if (body == null || body.getTenantId() == null) {
                return Optional.empty();
            }
            return Optional.of(body);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}

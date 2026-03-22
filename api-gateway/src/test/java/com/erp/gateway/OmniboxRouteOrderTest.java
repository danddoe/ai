package com.erp.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ensures {@code GET /v1/search/omnibox} resolves to global-search, not IAM {@code /v1/**}.
 */
@SpringBootTest
class OmniboxRouteOrderTest {

    @Autowired
    RouteLocator routeLocator;

    @Test
    void searchOmniboxUsesGlobalSearchRoute() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/v1/search/omnibox").queryParam("q", "en").build());

        List<Route> routes = routeLocator.getRoutes().collectList().block();
        assertThat(routes).isNotEmpty();

        Route matched = null;
        for (Route route : routes) {
            if (route.getPredicate().test(exchange)) {
                matched = route;
                break;
            }
        }

        assertThat(matched).as("expected a route to match /v1/search/omnibox").isNotNull();
        assertThat(matched.getId())
                .as("IAM /v1/** must not win over global-search for omnibox")
                .isEqualTo("global-search-omnibox");
    }
}

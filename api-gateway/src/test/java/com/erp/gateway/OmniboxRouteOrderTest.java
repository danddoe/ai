package com.erp.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ensures {@code GET /v1/search/omnibox} resolves to global-search, not IAM {@code /v1/**}.
 * Order from {@link RouteDefinitionLocator} matches first-wins route matching in Cloud Gateway.
 */
@SpringBootTest(properties = "spring.cloud.vault.enabled=false")
class OmniboxRouteOrderTest {

    @Autowired
    RouteDefinitionLocator routeDefinitionLocator;

    @Test
    void searchOmniboxUsesGlobalSearchRoute() {
        List<RouteDefinition> routes = routeDefinitionLocator.getRouteDefinitions()
                .collectList()
                .block(Duration.ofSeconds(10));
        assertThat(routes).isNotEmpty();

        int omnibox = indexOfRouteId(routes, "global-search-omnibox");
        int iamV1 = indexOfRouteId(routes, "iam-v1");
        assertThat(omnibox).as("global-search-omnibox route must be defined").isGreaterThanOrEqualTo(0);
        assertThat(iamV1).as("iam-v1 route must be defined").isGreaterThanOrEqualTo(0);
        assertThat(omnibox)
                .as("IAM /v1/** must not be ordered before global-search for omnibox paths")
                .isLessThan(iamV1);
    }

    private static int indexOfRouteId(List<RouteDefinition> routes, String id) {
        for (int i = 0; i < routes.size(); i++) {
            if (id.equals(routes.get(i).getId())) {
                return i;
            }
        }
        return -1;
    }
}

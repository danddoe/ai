package com.erp.iam.service;

import com.erp.iam.domain.PortalNavigationItem;
import com.erp.iam.repository.PortalNavigationItemRepository;
import com.erp.iam.security.TenantPrincipal;
import com.erp.iam.web.v1.dto.NavigationDtos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortalNavigationServiceSearchTest {

    @Mock
    PortalNavigationItemRepository repository;

    @InjectMocks
    PortalNavigationService service;

    @Test
    void search_returnsEmptyWhenQueryTooShort() {
        TenantPrincipal p = new TenantPrincipal(UUID.randomUUID(), UUID.randomUUID(), "a@b.c", List.of(), List.of());
        NavigationDtos.NavigationSearchResponse r = service.searchNavigation(p, "x", 10);
        assertThat(r.items()).isEmpty();
    }

    @Test
    void search_matchesSearchKeyword() {
        UUID id = UUID.randomUUID();
        PortalNavigationItem nav = new PortalNavigationItem();
        nav.setId(id);
        nav.setParentId(null);
        nav.setSortOrder(0);
        nav.setType("internal");
        nav.setRoutePath("/app/tax");
        nav.setLabel("Settings");
        nav.setDescription(null);
        nav.setSearchKeywords(List.of("tax", "vat"));
        nav.setRequiredPermissions(List.of());
        nav.setRequiredRoles(List.of());
        nav.setActive(true);

        when(repository.findAllByActiveTrueOrderByParentIdAscSortOrderAsc()).thenReturn(List.of(nav));

        TenantPrincipal p = new TenantPrincipal(UUID.randomUUID(), UUID.randomUUID(), "a@b.c", List.of(), List.of());
        NavigationDtos.NavigationSearchResponse r = service.searchNavigation(p, "tax", 10);
        assertThat(r.items()).hasSize(1);
        assertThat(r.items().get(0).routePath()).isEqualTo("/app/tax");
    }

    @Test
    void search_respectsRequiredPermission() {
        UUID id = UUID.randomUUID();
        PortalNavigationItem nav = new PortalNavigationItem();
        nav.setId(id);
        nav.setParentId(null);
        nav.setSortOrder(0);
        nav.setType("internal");
        nav.setRoutePath("/secret");
        nav.setLabel("Secret Tax");
        nav.setSearchKeywords(List.of("tax"));
        nav.setRequiredPermissions(List.of("entity_builder:schema:write"));
        nav.setRequiredRoles(List.of());
        nav.setActive(true);

        when(repository.findAllByActiveTrueOrderByParentIdAscSortOrderAsc()).thenReturn(List.of(nav));

        TenantPrincipal noPerm = new TenantPrincipal(UUID.randomUUID(), UUID.randomUUID(), "a@b.c", List.of(), List.of());
        assertThat(service.searchNavigation(noPerm, "tax", 10).items()).isEmpty();

        TenantPrincipal withPerm = new TenantPrincipal(UUID.randomUUID(), UUID.randomUUID(), "a@b.c", List.of(),
                List.of("entity_builder:schema:write"));
        assertThat(service.searchNavigation(withPerm, "tax", 10).items()).hasSize(1);
    }
}

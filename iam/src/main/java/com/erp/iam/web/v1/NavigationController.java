package com.erp.iam.web.v1;

import com.erp.iam.security.TenantPrincipal;
import com.erp.iam.service.PortalNavigationService;
import com.erp.iam.web.v1.dto.NavigationDtos;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/navigation")
public class NavigationController {

    private final PortalNavigationService portalNavigationService;

    public NavigationController(PortalNavigationService portalNavigationService) {
        this.portalNavigationService = portalNavigationService;
    }

    /**
     * Portal navigation tree for the current user. Entries are filtered using JWT permissions and roles.
     * Empty {@code required_permissions} / {@code required_roles} means no extra requirement beyond authentication.
     * Each item includes {@code categoryKey} (module bucket; inherited from ancestors when not set on the row) for grouped UI.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public NavigationDtos.NavigationResponse getNavigation() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        TenantPrincipal principal = (TenantPrincipal) auth.getPrincipal();
        return portalNavigationService.navigationFor(principal);
    }

    /**
     * Flat navigation search for omnibox; same permission/role filtering as {@link #getNavigation()}.
     */
    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    public NavigationDtos.NavigationSearchResponse searchNavigation(
            @RequestParam("q") String q,
            @RequestParam(value = "limit", defaultValue = "12") int limit
    ) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        TenantPrincipal principal = (TenantPrincipal) auth.getPrincipal();
        return portalNavigationService.searchNavigation(principal, q, limit);
    }
}

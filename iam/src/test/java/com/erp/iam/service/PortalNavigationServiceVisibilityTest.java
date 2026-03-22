package com.erp.iam.service;

import com.erp.iam.domain.PortalNavigationItem;
import com.erp.iam.security.TenantPrincipal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PortalNavigationServiceVisibilityTest {

    @Test
    void emptyRequirements_visible() {
        TenantPrincipal p = new TenantPrincipal(UUID.randomUUID(), UUID.randomUUID(), "a@b.c", List.of(), List.of());
        PortalNavigationItem item = new PortalNavigationItem();
        item.setRequiredPermissions(List.of());
        item.setRequiredRoles(List.of());
        assertThat(PortalNavigationService.visibleForPrincipal(p, item)).isTrue();
    }

    @Test
    void requiresPermission_anyMatch() {
        TenantPrincipal p = new TenantPrincipal(UUID.randomUUID(), UUID.randomUUID(), "a@b.c", List.of(), List.of("entity_builder:schema:read"));
        PortalNavigationItem item = new PortalNavigationItem();
        item.setRequiredPermissions(List.of("entity_builder:schema:write", "entity_builder:schema:read"));
        item.setRequiredRoles(List.of());
        assertThat(PortalNavigationService.visibleForPrincipal(p, item)).isTrue();
    }

    @Test
    void requiresPermission_noMatch() {
        TenantPrincipal p = new TenantPrincipal(UUID.randomUUID(), UUID.randomUUID(), "a@b.c", List.of(), List.of());
        PortalNavigationItem item = new PortalNavigationItem();
        item.setRequiredPermissions(List.of("entity_builder:schema:read"));
        item.setRequiredRoles(List.of());
        assertThat(PortalNavigationService.visibleForPrincipal(p, item)).isFalse();
    }

    @Test
    void requiresRole_andPermission_bothMustPass() {
        TenantPrincipal p = new TenantPrincipal(UUID.randomUUID(), UUID.randomUUID(), "a@b.c", List.of("ADMIN"), List.of("x"));
        PortalNavigationItem item = new PortalNavigationItem();
        item.setRequiredPermissions(List.of("x"));
        item.setRequiredRoles(List.of("SUPERADMIN"));
        assertThat(PortalNavigationService.visibleForPrincipal(p, item)).isFalse();

        TenantPrincipal p2 = new TenantPrincipal(UUID.randomUUID(), UUID.randomUUID(), "a@b.c", List.of("SUPERADMIN"), List.of("x"));
        assertThat(PortalNavigationService.visibleForPrincipal(p2, item)).isTrue();
    }

    @Test
    void tenantScoped_row_sameTenant_visible() {
        UUID tid = UUID.randomUUID();
        TenantPrincipal p = new TenantPrincipal(UUID.randomUUID(), tid, "a@b.c", List.of(), List.of());
        PortalNavigationItem item = new PortalNavigationItem();
        item.setTenantId(tid);
        item.setRequiredPermissions(List.of());
        item.setRequiredRoles(List.of());
        assertThat(PortalNavigationService.visibleForPrincipal(p, item)).isTrue();
    }

    @Test
    void tenantScoped_row_otherTenant_hidden() {
        UUID tidA = UUID.randomUUID();
        UUID tidB = UUID.randomUUID();
        TenantPrincipal p = new TenantPrincipal(UUID.randomUUID(), tidA, "a@b.c", List.of(), List.of());
        PortalNavigationItem item = new PortalNavigationItem();
        item.setTenantId(tidB);
        item.setRequiredPermissions(List.of());
        item.setRequiredRoles(List.of());
        assertThat(PortalNavigationService.visibleForPrincipal(p, item)).isFalse();
    }

    @Test
    void globalRow_nullTenant_visibleRegardlessOfUserTenant() {
        UUID tid = UUID.randomUUID();
        TenantPrincipal p = new TenantPrincipal(UUID.randomUUID(), tid, "a@b.c", List.of(), List.of());
        PortalNavigationItem item = new PortalNavigationItem();
        item.setTenantId(null);
        item.setRequiredPermissions(List.of());
        item.setRequiredRoles(List.of());
        assertThat(PortalNavigationService.visibleForPrincipal(p, item)).isTrue();
    }
}

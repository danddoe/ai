package com.erp.entitybuilder.web.v1;

import com.erp.entitybuilder.service.catalog.SystemCatalogSyncService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class SystemCatalogController {

    private final SystemCatalogSyncService catalogSyncService;

    public SystemCatalogController(SystemCatalogSyncService catalogSyncService) {
        this.catalogSyncService = catalogSyncService;
    }

    /**
     * Idempotent upsert of classpath manifests ({@code system-entity-catalog/*.json}) into {@code entities} / {@code entity_fields}.
     */
    @PostMapping("/v1/tenants/{tenantId}/catalog/sync")
    @PreAuthorize("(hasAuthority('entity_builder:schema:write') and @entityBuilderSecurity.isTenant(#tenantId)) or @entityBuilderSecurity.hasCrossTenantAdminAuthority()")
    public SystemCatalogSyncService.CatalogSyncResult sync(
            @PathVariable UUID tenantId,
            @RequestParam(required = false) String manifestKey
    ) {
        return catalogSyncService.sync(tenantId, manifestKey);
    }
}

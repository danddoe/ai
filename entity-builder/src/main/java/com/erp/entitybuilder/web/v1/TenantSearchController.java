package com.erp.entitybuilder.web.v1;

import com.erp.entitybuilder.service.GlobalRecordSearchService;
import com.erp.entitybuilder.web.v1.dto.GlobalRecordSearchDtos;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/tenants/{tenantId}/search")
public class TenantSearchController {

    private final GlobalRecordSearchService globalRecordSearchService;

    public TenantSearchController(GlobalRecordSearchService globalRecordSearchService) {
        this.globalRecordSearchService = globalRecordSearchService;
    }

    @GetMapping("/records")
    @PreAuthorize("(hasAuthority('entity_builder:records:read') and @entityBuilderSecurity.isTenant(#tenantId)) or @entityBuilderSecurity.hasCrossTenantAdminAuthority()")
    public GlobalRecordSearchDtos.GlobalRecordSearchResponse searchRecords(
            @PathVariable UUID tenantId,
            @RequestParam("q") String q,
            @RequestParam(value = "limit", defaultValue = "30") int limit
    ) {
        return globalRecordSearchService.search(tenantId, q, limit);
    }
}

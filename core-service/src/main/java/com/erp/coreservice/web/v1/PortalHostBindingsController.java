package com.erp.coreservice.web.v1;

import com.erp.coreservice.domain.PortalHostBinding;
import com.erp.coreservice.service.PortalHostBindingService;
import com.erp.coreservice.web.v1.dto.PortalDtos;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/tenants/{tenantId}/portal-host-bindings")
public class PortalHostBindingsController {

    private final PortalHostBindingService portalHostBindingService;

    public PortalHostBindingsController(PortalHostBindingService portalHostBindingService) {
        this.portalHostBindingService = portalHostBindingService;
    }

    @GetMapping
    @PreAuthorize("(hasAuthority('master_data:companies:read') and @coreServiceSecurity.isTenant(#tenantId)) or @coreServiceSecurity.hasElevatedAccess()")
    public List<PortalDtos.PortalHostBindingDto> list(@PathVariable UUID tenantId) {
        return portalHostBindingService.listForTenant(tenantId).stream()
                .map(PortalHostBindingService::toDto)
                .toList();
    }

    @GetMapping("/{bindingId}")
    @PreAuthorize("(hasAuthority('master_data:companies:read') and @coreServiceSecurity.isTenant(#tenantId)) or @coreServiceSecurity.hasElevatedAccess()")
    public PortalDtos.PortalHostBindingDto get(@PathVariable UUID tenantId, @PathVariable UUID bindingId) {
        return PortalHostBindingService.toDto(portalHostBindingService.get(tenantId, bindingId));
    }

    @PostMapping
    @PreAuthorize("(hasAuthority('master_data:companies:write') and @coreServiceSecurity.isTenant(#tenantId)) or @coreServiceSecurity.hasElevatedAccess()")
    public PortalDtos.PortalHostBindingDto create(
            @PathVariable UUID tenantId,
            @Valid @RequestBody PortalDtos.CreatePortalHostBindingRequest req
    ) {
        return PortalHostBindingService.toDto(portalHostBindingService.create(tenantId, req));
    }

    @PatchMapping("/{bindingId}")
    @PreAuthorize("(hasAuthority('master_data:companies:write') and @coreServiceSecurity.isTenant(#tenantId)) or @coreServiceSecurity.hasElevatedAccess()")
    public PortalDtos.PortalHostBindingDto patch(
            @PathVariable UUID tenantId,
            @PathVariable UUID bindingId,
            @Valid @RequestBody PortalDtos.PatchPortalHostBindingRequest req
    ) {
        return PortalHostBindingService.toDto(portalHostBindingService.patch(tenantId, bindingId, req));
    }

    @DeleteMapping("/{bindingId}")
    @PreAuthorize("(hasAuthority('master_data:companies:write') and @coreServiceSecurity.isTenant(#tenantId)) or @coreServiceSecurity.hasElevatedAccess()")
    public void delete(@PathVariable UUID tenantId, @PathVariable UUID bindingId) {
        portalHostBindingService.delete(tenantId, bindingId);
    }
}

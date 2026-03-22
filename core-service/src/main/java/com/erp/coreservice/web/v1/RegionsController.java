package com.erp.coreservice.web.v1;

import com.erp.coreservice.domain.Region;
import com.erp.coreservice.service.RegionService;
import com.erp.coreservice.web.v1.dto.OrgDtos;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/tenants/{tenantId}/regions")
public class RegionsController {

    private final RegionService regionService;

    public RegionsController(RegionService regionService) {
        this.regionService = regionService;
    }

    @PostMapping
    @PreAuthorize("(hasAuthority('master_data:regions:write') and @coreServiceSecurity.isTenant(#tenantId)) or @coreServiceSecurity.hasElevatedAccess()")
    public OrgDtos.RegionDto create(
            @PathVariable UUID tenantId,
            @Valid @RequestBody OrgDtos.CreateRegionRequest req
    ) {
        return toDto(regionService.create(tenantId, req));
    }

    @GetMapping
    @PreAuthorize("(hasAuthority('master_data:regions:read') and @coreServiceSecurity.isTenant(#tenantId)) or @coreServiceSecurity.hasElevatedAccess()")
    public OrgDtos.PageResponse<OrgDtos.RegionDto> list(
            @PathVariable UUID tenantId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize
    ) {
        Page<Region> p = regionService.list(tenantId, page, pageSize);
        return new OrgDtos.PageResponse<>(
                p.getContent().stream().map(RegionsController::toDto).toList(),
                page,
                p.getSize(),
                p.getTotalElements()
        );
    }

    @GetMapping("/{regionId}")
    @PreAuthorize("(hasAuthority('master_data:regions:read') and @coreServiceSecurity.isTenant(#tenantId)) or @coreServiceSecurity.hasElevatedAccess()")
    public OrgDtos.RegionDto get(@PathVariable UUID tenantId, @PathVariable UUID regionId) {
        return toDto(regionService.get(tenantId, regionId));
    }

    @PatchMapping("/{regionId}")
    @PreAuthorize("(hasAuthority('master_data:regions:write') and @coreServiceSecurity.isTenant(#tenantId)) or @coreServiceSecurity.hasElevatedAccess()")
    public OrgDtos.RegionDto patch(
            @PathVariable UUID tenantId,
            @PathVariable UUID regionId,
            @Valid @RequestBody OrgDtos.PatchRegionRequest req
    ) {
        return toDto(regionService.patch(tenantId, regionId, req));
    }

    private static OrgDtos.RegionDto toDto(Region r) {
        return new OrgDtos.RegionDto(
                r.getRegionId(),
                r.getTenantId(),
                r.getParentRegionId(),
                r.getRegionCode(),
                r.getRegionName(),
                r.getCreatedAt(),
                r.getUpdatedAt()
        );
    }
}

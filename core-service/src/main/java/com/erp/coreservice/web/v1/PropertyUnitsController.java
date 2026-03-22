package com.erp.coreservice.web.v1;

import com.erp.coreservice.domain.PropertyUnit;
import com.erp.coreservice.service.PropertyUnitService;
import com.erp.coreservice.web.v1.dto.OrgDtos;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/tenants/{tenantId}/property-units")
public class PropertyUnitsController {

    private final PropertyUnitService propertyUnitService;

    public PropertyUnitsController(PropertyUnitService propertyUnitService) {
        this.propertyUnitService = propertyUnitService;
    }

    @PostMapping
    @PreAuthorize("(hasAuthority('master_data:property_units:write') and @coreServiceSecurity.isTenant(#tenantId)) or @coreServiceSecurity.hasElevatedAccess()")
    public OrgDtos.PropertyUnitDto create(
            @PathVariable UUID tenantId,
            @Valid @RequestBody OrgDtos.CreatePropertyUnitRequest req
    ) {
        return toDto(propertyUnitService.create(tenantId, req));
    }

    @GetMapping
    @PreAuthorize("(hasAuthority('master_data:property_units:read') and @coreServiceSecurity.isTenant(#tenantId)) or @coreServiceSecurity.hasElevatedAccess()")
    public OrgDtos.PageResponse<OrgDtos.PropertyUnitDto> list(
            @PathVariable UUID tenantId,
            @RequestParam(required = false) UUID propertyId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize
    ) {
        Page<PropertyUnit> p = propertyUnitService.list(tenantId, propertyId, page, pageSize);
        return new OrgDtos.PageResponse<>(
                p.getContent().stream().map(PropertyUnitsController::toDto).toList(),
                page,
                p.getSize(),
                p.getTotalElements()
        );
    }

    @GetMapping("/{unitId}")
    @PreAuthorize("(hasAuthority('master_data:property_units:read') and @coreServiceSecurity.isTenant(#tenantId)) or @coreServiceSecurity.hasElevatedAccess()")
    public OrgDtos.PropertyUnitDto get(@PathVariable UUID tenantId, @PathVariable UUID unitId) {
        return toDto(propertyUnitService.get(tenantId, unitId));
    }

    @PatchMapping("/{unitId}")
    @PreAuthorize("(hasAuthority('master_data:property_units:write') and @coreServiceSecurity.isTenant(#tenantId)) or @coreServiceSecurity.hasElevatedAccess()")
    public OrgDtos.PropertyUnitDto patch(
            @PathVariable UUID tenantId,
            @PathVariable UUID unitId,
            @Valid @RequestBody OrgDtos.PatchPropertyUnitRequest req
    ) {
        return toDto(propertyUnitService.patch(tenantId, unitId, req));
    }

    private static OrgDtos.PropertyUnitDto toDto(PropertyUnit u) {
        return new OrgDtos.PropertyUnitDto(
                u.getUnitId(),
                u.getTenantId(),
                u.getPropertyId(),
                u.getUnitNumber(),
                u.getSquareFootage(),
                u.getStatus(),
                u.getCreatedAt(),
                u.getUpdatedAt()
        );
    }
}

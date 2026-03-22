package com.erp.coreservice.web.v1;

import com.erp.coreservice.domain.Property;
import com.erp.coreservice.service.PropertyService;
import com.erp.coreservice.web.v1.dto.OrgDtos;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/tenants/{tenantId}/properties")
public class PropertiesController {

    private final PropertyService propertyService;

    public PropertiesController(PropertyService propertyService) {
        this.propertyService = propertyService;
    }

    @PostMapping
    @PreAuthorize("(hasAuthority('master_data:properties:write') and @coreServiceSecurity.isTenant(#tenantId)) or @coreServiceSecurity.hasElevatedAccess()")
    public OrgDtos.PropertyDto create(
            @PathVariable UUID tenantId,
            @Valid @RequestBody OrgDtos.CreatePropertyRequest req
    ) {
        return toDto(propertyService.create(tenantId, req));
    }

    @GetMapping
    @PreAuthorize("(hasAuthority('master_data:properties:read') and @coreServiceSecurity.isTenant(#tenantId)) or @coreServiceSecurity.hasElevatedAccess()")
    public OrgDtos.PageResponse<OrgDtos.PropertyDto> list(
            @PathVariable UUID tenantId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize
    ) {
        Page<Property> p = propertyService.list(tenantId, page, pageSize);
        return new OrgDtos.PageResponse<>(
                p.getContent().stream().map(PropertiesController::toDto).toList(),
                page,
                p.getSize(),
                p.getTotalElements()
        );
    }

    @GetMapping("/{propertyId}")
    @PreAuthorize("(hasAuthority('master_data:properties:read') and @coreServiceSecurity.isTenant(#tenantId)) or @coreServiceSecurity.hasElevatedAccess()")
    public OrgDtos.PropertyDto get(@PathVariable UUID tenantId, @PathVariable UUID propertyId) {
        return toDto(propertyService.get(tenantId, propertyId));
    }

    @PatchMapping("/{propertyId}")
    @PreAuthorize("(hasAuthority('master_data:properties:write') and @coreServiceSecurity.isTenant(#tenantId)) or @coreServiceSecurity.hasElevatedAccess()")
    public OrgDtos.PropertyDto patch(
            @PathVariable UUID tenantId,
            @PathVariable UUID propertyId,
            @Valid @RequestBody OrgDtos.PatchPropertyRequest req
    ) {
        return toDto(propertyService.patch(tenantId, propertyId, req));
    }

    private static OrgDtos.PropertyDto toDto(Property p) {
        return new OrgDtos.PropertyDto(
                p.getPropertyId(),
                p.getTenantId(),
                p.getCompanyId(),
                p.getLocationId(),
                p.getPropertyName(),
                p.getPropertyType(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}

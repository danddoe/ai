package com.erp.coreservice.web.v1;

import com.erp.coreservice.domain.Location;
import com.erp.coreservice.service.LocationService;
import com.erp.coreservice.web.v1.dto.OrgDtos;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/tenants/{tenantId}/locations")
public class LocationsController {

    private final LocationService locationService;

    public LocationsController(LocationService locationService) {
        this.locationService = locationService;
    }

    @PostMapping
    @PreAuthorize("(hasAuthority('master_data:locations:write') and @coreServiceSecurity.isTenant(#tenantId)) or @coreServiceSecurity.hasElevatedAccess()")
    public OrgDtos.LocationDto create(
            @PathVariable UUID tenantId,
            @Valid @RequestBody OrgDtos.CreateLocationRequest req
    ) {
        return toDto(locationService.create(tenantId, req));
    }

    @GetMapping
    @PreAuthorize("(hasAuthority('master_data:locations:read') and @coreServiceSecurity.isTenant(#tenantId)) or @coreServiceSecurity.hasElevatedAccess()")
    public OrgDtos.PageResponse<OrgDtos.LocationDto> list(
            @PathVariable UUID tenantId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize
    ) {
        Page<Location> p = locationService.list(tenantId, page, pageSize);
        return new OrgDtos.PageResponse<>(
                p.getContent().stream().map(LocationsController::toDto).toList(),
                page,
                p.getSize(),
                p.getTotalElements()
        );
    }

    @GetMapping("/{locationId}")
    @PreAuthorize("(hasAuthority('master_data:locations:read') and @coreServiceSecurity.isTenant(#tenantId)) or @coreServiceSecurity.hasElevatedAccess()")
    public OrgDtos.LocationDto get(@PathVariable UUID tenantId, @PathVariable UUID locationId) {
        return toDto(locationService.get(tenantId, locationId));
    }

    @PatchMapping("/{locationId}")
    @PreAuthorize("(hasAuthority('master_data:locations:write') and @coreServiceSecurity.isTenant(#tenantId)) or @coreServiceSecurity.hasElevatedAccess()")
    public OrgDtos.LocationDto patch(
            @PathVariable UUID tenantId,
            @PathVariable UUID locationId,
            @Valid @RequestBody OrgDtos.PatchLocationRequest req
    ) {
        return toDto(locationService.patch(tenantId, locationId, req));
    }

    private static OrgDtos.LocationDto toDto(Location loc) {
        return new OrgDtos.LocationDto(
                loc.getLocationId(),
                loc.getTenantId(),
                loc.getLocationName(),
                loc.getAddressLine1(),
                loc.getCity(),
                loc.getStateProvince(),
                loc.getPostalCode(),
                loc.getCountryCode(),
                loc.getRegionId(),
                loc.getCreatedAt(),
                loc.getUpdatedAt()
        );
    }
}

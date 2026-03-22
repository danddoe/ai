package com.erp.coreservice.web.v1;

import com.erp.coreservice.domain.BusinessUnit;
import com.erp.coreservice.service.BusinessUnitService;
import com.erp.coreservice.web.v1.dto.OrgDtos;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/tenants/{tenantId}/business-units")
public class BusinessUnitsController {

    private final BusinessUnitService businessUnitService;

    public BusinessUnitsController(BusinessUnitService businessUnitService) {
        this.businessUnitService = businessUnitService;
    }

    @PostMapping
    @PreAuthorize("(hasAuthority('master_data:business_units:write') and @coreServiceSecurity.isTenant(#tenantId)) or @coreServiceSecurity.hasElevatedAccess()")
    public OrgDtos.BusinessUnitDto create(
            @PathVariable UUID tenantId,
            @Valid @RequestBody OrgDtos.CreateBusinessUnitRequest req
    ) {
        return toDto(businessUnitService.create(tenantId, req));
    }

    @GetMapping
    @PreAuthorize("(hasAuthority('master_data:business_units:read') and @coreServiceSecurity.isTenant(#tenantId)) or @coreServiceSecurity.hasElevatedAccess()")
    public OrgDtos.PageResponse<OrgDtos.BusinessUnitDto> list(
            @PathVariable UUID tenantId,
            @RequestParam(required = false) UUID companyId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize
    ) {
        Page<BusinessUnit> p = businessUnitService.list(tenantId, companyId, page, pageSize);
        return new OrgDtos.PageResponse<>(
                p.getContent().stream().map(BusinessUnitsController::toDto).toList(),
                page,
                p.getSize(),
                p.getTotalElements()
        );
    }

    @GetMapping("/{buId}")
    @PreAuthorize("(hasAuthority('master_data:business_units:read') and @coreServiceSecurity.isTenant(#tenantId)) or @coreServiceSecurity.hasElevatedAccess()")
    public OrgDtos.BusinessUnitDto get(@PathVariable UUID tenantId, @PathVariable UUID buId) {
        return toDto(businessUnitService.get(tenantId, buId));
    }

    @PatchMapping("/{buId}")
    @PreAuthorize("(hasAuthority('master_data:business_units:write') and @coreServiceSecurity.isTenant(#tenantId)) or @coreServiceSecurity.hasElevatedAccess()")
    public OrgDtos.BusinessUnitDto patch(
            @PathVariable UUID tenantId,
            @PathVariable UUID buId,
            @Valid @RequestBody OrgDtos.PatchBusinessUnitRequest req
    ) {
        return toDto(businessUnitService.patch(tenantId, buId, req));
    }

    private static OrgDtos.BusinessUnitDto toDto(BusinessUnit bu) {
        return new OrgDtos.BusinessUnitDto(
                bu.getBuId(),
                bu.getTenantId(),
                bu.getCompanyId(),
                bu.getParentBuId(),
                bu.getBuType(),
                bu.getBuName(),
                bu.getCreatedAt(),
                bu.getUpdatedAt()
        );
    }
}

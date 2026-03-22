package com.erp.coreservice.web.v1;

import com.erp.coreservice.domain.Company;
import com.erp.coreservice.service.CompanyService;
import com.erp.coreservice.web.v1.dto.OrgDtos;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/tenants/{tenantId}/companies")
public class CompaniesController {

    private final CompanyService companyService;

    public CompaniesController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @PostMapping
    @PreAuthorize("(hasAuthority('master_data:companies:write') and @coreServiceSecurity.isTenant(#tenantId)) or @coreServiceSecurity.hasElevatedAccess()")
    public OrgDtos.CompanyDto create(
            @PathVariable UUID tenantId,
            @Valid @RequestBody OrgDtos.CreateCompanyRequest req
    ) {
        return toDto(companyService.create(tenantId, req));
    }

    @GetMapping
    @PreAuthorize("(hasAuthority('master_data:companies:read') and @coreServiceSecurity.isTenant(#tenantId)) or @coreServiceSecurity.hasElevatedAccess()")
    public OrgDtos.PageResponse<OrgDtos.CompanyDto> list(
            @PathVariable UUID tenantId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize
    ) {
        Page<Company> p = companyService.list(tenantId, page, pageSize);
        return new OrgDtos.PageResponse<>(
                p.getContent().stream().map(CompaniesController::toDto).toList(),
                page,
                p.getSize(),
                p.getTotalElements()
        );
    }

    @GetMapping("/{companyId}")
    @PreAuthorize("(hasAuthority('master_data:companies:read') and @coreServiceSecurity.isTenant(#tenantId)) or @coreServiceSecurity.hasElevatedAccess()")
    public OrgDtos.CompanyDto get(@PathVariable UUID tenantId, @PathVariable UUID companyId) {
        return toDto(companyService.get(tenantId, companyId));
    }

    @PatchMapping("/{companyId}")
    @PreAuthorize("(hasAuthority('master_data:companies:write') and @coreServiceSecurity.isTenant(#tenantId)) or @coreServiceSecurity.hasElevatedAccess()")
    public OrgDtos.CompanyDto patch(
            @PathVariable UUID tenantId,
            @PathVariable UUID companyId,
            @Valid @RequestBody OrgDtos.PatchCompanyRequest req
    ) {
        return toDto(companyService.patch(tenantId, companyId, req));
    }

    private static OrgDtos.CompanyDto toDto(Company c) {
        return new OrgDtos.CompanyDto(
                c.getCompanyId(),
                c.getTenantId(),
                c.getParentCompanyId(),
                c.getCompanyName(),
                c.getOwnershipPct(),
                c.getBaseCurrency(),
                c.getDefaultPortalBuId(),
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }
}

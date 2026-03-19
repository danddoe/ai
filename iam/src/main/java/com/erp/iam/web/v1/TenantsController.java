package com.erp.iam.web.v1;

import com.erp.iam.domain.Tenant;
import com.erp.iam.service.IamAdminService;
import com.erp.iam.web.v1.dto.PageResponse;
import com.erp.iam.web.v1.dto.TenantDtos;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/v1/tenants")
public class TenantsController {

    private final IamAdminService iamAdminService;

    public TenantsController(IamAdminService iamAdminService) {
        this.iamAdminService = iamAdminService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('iam:tenants:admin') or @iamSecurity.isSuperAdmin()")
    public PageResponse<TenantDtos.TenantDto> list(
            @RequestParam Optional<String> status,
            @RequestParam Optional<String> q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize
    ) {
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), clampPageSize(pageSize));
        Page<Tenant> result = iamAdminService.listTenants(status, q, pageable);
        return new PageResponse<>(
                result.getContent().stream().map(TenantsController::toDto).toList(),
                page,
                result.getSize(),
                result.getTotalElements()
        );
    }

    @PostMapping
    @PreAuthorize("hasAuthority('iam:tenants:admin') or @iamSecurity.isSuperAdmin()")
    public TenantDtos.TenantDto create(@Valid @RequestBody TenantDtos.CreateTenantRequest req) {
        Tenant t = iamAdminService.createTenant(req.getName(), req.getSlug(), req.getStatus());
        return toDto(t);
    }

    @GetMapping("/{tenantId}")
    @PreAuthorize("hasAuthority('iam:tenants:admin') or @iamSecurity.isSuperAdmin()")
    public TenantDtos.TenantDto get(@PathVariable UUID tenantId) {
        return toDto(iamAdminService.getTenant(tenantId));
    }

    @PatchMapping("/{tenantId}")
    @PreAuthorize("hasAuthority('iam:tenants:admin') or @iamSecurity.isSuperAdmin()")
    public TenantDtos.TenantDto update(@PathVariable UUID tenantId, @Valid @RequestBody TenantDtos.UpdateTenantRequest req) {
        Tenant t = iamAdminService.updateTenant(
                tenantId,
                Optional.ofNullable(req.getName()),
                Optional.ofNullable(req.getSlug()),
                Optional.ofNullable(req.getStatus())
        );
        return toDto(t);
    }

    @DeleteMapping("/{tenantId}")
    @PreAuthorize("hasAuthority('iam:tenants:admin') or @iamSecurity.isSuperAdmin()")
    public void delete(@PathVariable UUID tenantId) {
        iamAdminService.deleteTenant(tenantId);
    }

    private static TenantDtos.TenantDto toDto(Tenant t) {
        return new TenantDtos.TenantDto(t.getId(), t.getName(), t.getSlug(), t.getStatus(), t.getCreatedAt(), t.getUpdatedAt());
    }

    private static int clampPageSize(int pageSize) {
        if (pageSize <= 0) return 50;
        return Math.min(pageSize, 200);
    }
}


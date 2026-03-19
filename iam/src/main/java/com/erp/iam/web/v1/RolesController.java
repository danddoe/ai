package com.erp.iam.web.v1;

import com.erp.iam.domain.Role;
import com.erp.iam.service.IamAdminService;
import com.erp.iam.web.v1.dto.PageResponse;
import com.erp.iam.web.v1.dto.RoleDtos;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/v1/tenants/{tenantId}/roles")
public class RolesController {

    private final IamAdminService iamAdminService;

    public RolesController(IamAdminService iamAdminService) {
        this.iamAdminService = iamAdminService;
    }

    @GetMapping
    @PreAuthorize("(hasAuthority('iam:roles:read') and @iamSecurity.isTenant(#tenantId)) or hasAuthority('iam:security:admin') or @iamSecurity.isSuperAdmin()")
    public PageResponse<RoleDtos.RoleDto> list(
            @PathVariable UUID tenantId,
            @RequestParam Optional<String> q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize
    ) {
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), clampPageSize(pageSize));
        Page<Role> result = iamAdminService.listRoles(tenantId, q, pageable);
        return new PageResponse<>(
                result.getContent().stream().map(RolesController::toDto).toList(),
                page,
                result.getSize(),
                result.getTotalElements()
        );
    }

    @PostMapping
    @PreAuthorize("(hasAuthority('iam:roles:write') and @iamSecurity.isTenant(#tenantId)) or hasAuthority('iam:security:admin') or @iamSecurity.isSuperAdmin()")
    public RoleDtos.RoleDto create(@PathVariable UUID tenantId, @Valid @RequestBody RoleDtos.CreateRoleRequest req) {
        Role r = iamAdminService.createRole(tenantId, req.getName(), req.getDescription(), req.isSystem());
        return toDto(r);
    }

    @GetMapping("/{roleId}")
    @PreAuthorize("(hasAuthority('iam:roles:read') and @iamSecurity.isTenant(#tenantId)) or hasAuthority('iam:security:admin') or @iamSecurity.isSuperAdmin()")
    public RoleDtos.RoleDto get(@PathVariable UUID tenantId, @PathVariable UUID roleId) {
        return toDto(iamAdminService.getRole(tenantId, roleId));
    }

    @PatchMapping("/{roleId}")
    @PreAuthorize("(hasAuthority('iam:roles:write') and @iamSecurity.isTenant(#tenantId)) or hasAuthority('iam:security:admin') or @iamSecurity.isSuperAdmin()")
    public RoleDtos.RoleDto update(@PathVariable UUID tenantId, @PathVariable UUID roleId, @Valid @RequestBody RoleDtos.UpdateRoleRequest req) {
        Role r = iamAdminService.updateRole(
                tenantId,
                roleId,
                Optional.ofNullable(req.getName()),
                Optional.ofNullable(req.getDescription())
        );
        return toDto(r);
    }

    @DeleteMapping("/{roleId}")
    @PreAuthorize("(hasAuthority('iam:roles:write') and @iamSecurity.isTenant(#tenantId)) or hasAuthority('iam:security:admin') or @iamSecurity.isSuperAdmin()")
    public void delete(@PathVariable UUID tenantId, @PathVariable UUID roleId) {
        boolean allowSystem = SecurityUtil.hasAuthority("iam:security:admin") || SecurityUtil.hasAuthority("iam:superadmin");
        iamAdminService.deleteRole(tenantId, roleId, allowSystem);
    }

    private static RoleDtos.RoleDto toDto(Role r) {
        return new RoleDtos.RoleDto(
                r.getId(),
                r.getTenantId(),
                r.getName(),
                r.getDescription(),
                r.isSystem(),
                r.getCreatedAt(),
                r.getUpdatedAt()
        );
    }

    private static int clampPageSize(int pageSize) {
        if (pageSize <= 0) return 50;
        return Math.min(pageSize, 200);
    }
}


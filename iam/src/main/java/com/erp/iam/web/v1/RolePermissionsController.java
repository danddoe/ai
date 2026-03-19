package com.erp.iam.web.v1;

import com.erp.iam.domain.Permission;
import com.erp.iam.service.IamAdminService;
import com.erp.iam.web.v1.dto.AssignmentDtos;
import com.erp.iam.web.v1.dto.PermissionDtos;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/tenants/{tenantId}/roles/{roleId}/permissions")
public class RolePermissionsController {

    private final IamAdminService iamAdminService;

    public RolePermissionsController(IamAdminService iamAdminService) {
        this.iamAdminService = iamAdminService;
    }

    @GetMapping
    @PreAuthorize("(hasAuthority('iam:roles:read') and @iamSecurity.isTenant(#tenantId)) or hasAuthority('iam:security:admin') or @iamSecurity.isSuperAdmin()")
    public List<PermissionDtos.PermissionDto> list(@PathVariable UUID tenantId, @PathVariable UUID roleId) {
        List<Permission> perms = iamAdminService.listRolePermissions(tenantId, roleId);
        return perms.stream().map(p -> new PermissionDtos.PermissionDto(p.getId(), p.getCode(), p.getDescription(), p.getCreatedAt(), p.getUpdatedAt())).toList();
    }

    @PutMapping
    @PreAuthorize("(hasAuthority('iam:roles:write') and @iamSecurity.isTenant(#tenantId)) or hasAuthority('iam:security:admin') or @iamSecurity.isSuperAdmin()")
    public void replace(
            @PathVariable UUID tenantId,
            @PathVariable UUID roleId,
            @Valid @RequestBody AssignmentDtos.ReplaceRolePermissionsRequest req
    ) {
        iamAdminService.replaceRolePermissions(tenantId, roleId, req.getPermissionIds());
    }
}


package com.erp.iam.web.v1;

import com.erp.iam.domain.Role;
import com.erp.iam.service.IamAdminService;
import com.erp.iam.web.v1.dto.AssignmentDtos;
import com.erp.iam.web.v1.dto.RoleDtos;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/tenants/{tenantId}/users/{userId}/roles")
public class UserRolesController {

    private final IamAdminService iamAdminService;

    public UserRolesController(IamAdminService iamAdminService) {
        this.iamAdminService = iamAdminService;
    }

    @GetMapping
    @PreAuthorize("(hasAuthority('iam:tenant_users:read') and @iamSecurity.isTenant(#tenantId)) or hasAuthority('iam:security:admin') or @iamSecurity.isSuperAdmin()")
    public List<RoleDtos.RoleDto> list(@PathVariable UUID tenantId, @PathVariable UUID userId) {
        List<Role> roles = iamAdminService.listUserRoles(tenantId, userId);
        return roles.stream().map(r -> new RoleDtos.RoleDto(r.getId(), r.getTenantId(), r.getName(), r.getDescription(), r.isSystem(), r.getCreatedAt(), r.getUpdatedAt())).toList();
    }

    @PutMapping
    @PreAuthorize("(hasAuthority('iam:tenant_users:write') and @iamSecurity.isTenant(#tenantId)) or hasAuthority('iam:security:admin') or @iamSecurity.isSuperAdmin()")
    public void replace(@PathVariable UUID tenantId, @PathVariable UUID userId, @Valid @RequestBody AssignmentDtos.ReplaceUserRolesRequest req) {
        iamAdminService.replaceUserRoles(tenantId, userId, req.getRoleIds());
    }
}


package com.erp.iam.web.v1;

import com.erp.iam.domain.Permission;
import com.erp.iam.service.IamAdminService;
import com.erp.iam.web.v1.dto.PageResponse;
import com.erp.iam.web.v1.dto.PermissionDtos;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/v1/permissions")
public class PermissionsController {

    private final IamAdminService iamAdminService;

    public PermissionsController(IamAdminService iamAdminService) {
        this.iamAdminService = iamAdminService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('iam:roles:read') or hasAuthority('iam:permissions:admin') or hasAuthority('iam:security:admin') or @iamSecurity.isSuperAdmin()")
    public PageResponse<PermissionDtos.PermissionDto> list(
            @RequestParam Optional<String> q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize
    ) {
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), clampPageSize(pageSize));
        Page<Permission> result = iamAdminService.listPermissions(q, pageable);
        return new PageResponse<>(
                result.getContent().stream().map(PermissionsController::toDto).toList(),
                page,
                result.getSize(),
                result.getTotalElements()
        );
    }

    @PostMapping
    @PreAuthorize("hasAuthority('iam:permissions:admin') or @iamSecurity.isSuperAdmin()")
    public PermissionDtos.PermissionDto create(@Valid @RequestBody PermissionDtos.CreatePermissionRequest req) {
        return toDto(iamAdminService.createPermission(req.getCode(), req.getDescription()));
    }

    @GetMapping("/{permissionId}")
    @PreAuthorize("hasAuthority('iam:roles:read') or hasAuthority('iam:permissions:admin') or hasAuthority('iam:security:admin') or @iamSecurity.isSuperAdmin()")
    public PermissionDtos.PermissionDto get(@PathVariable UUID permissionId) {
        return toDto(iamAdminService.getPermission(permissionId));
    }

    @PatchMapping("/{permissionId}")
    @PreAuthorize("hasAuthority('iam:permissions:admin') or @iamSecurity.isSuperAdmin()")
    public PermissionDtos.PermissionDto update(@PathVariable UUID permissionId, @Valid @RequestBody PermissionDtos.UpdatePermissionRequest req) {
        Permission p = iamAdminService.updatePermission(
                permissionId,
                Optional.ofNullable(req.getCode()),
                Optional.ofNullable(req.getDescription())
        );
        return toDto(p);
    }

    @DeleteMapping("/{permissionId}")
    @PreAuthorize("hasAuthority('iam:permissions:admin') or @iamSecurity.isSuperAdmin()")
    public void delete(@PathVariable UUID permissionId) {
        iamAdminService.deletePermission(permissionId);
    }

    private static PermissionDtos.PermissionDto toDto(Permission p) {
        return new PermissionDtos.PermissionDto(p.getId(), p.getCode(), p.getDescription(), p.getCreatedAt(), p.getUpdatedAt());
    }

    private static int clampPageSize(int pageSize) {
        if (pageSize <= 0) return 50;
        return Math.min(pageSize, 200);
    }
}


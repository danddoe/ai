package com.erp.iam.web.v1;

import com.erp.iam.domain.TenantUser;
import com.erp.iam.domain.User;
import com.erp.iam.service.IamAdminService;
import com.erp.iam.web.ApiException;
import com.erp.iam.web.v1.dto.PageResponse;
import com.erp.iam.web.v1.dto.TenantMemberDtos;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/v1/tenants/{tenantId}/members")
public class TenantMembersController {

    private final IamAdminService iamAdminService;

    public TenantMembersController(IamAdminService iamAdminService) {
        this.iamAdminService = iamAdminService;
    }

    @GetMapping
    @PreAuthorize("(hasAuthority('iam:tenant_users:read') and @iamSecurity.isTenant(#tenantId)) or hasAuthority('iam:security:admin') or @iamSecurity.isSuperAdmin()")
    public PageResponse<TenantMemberDtos.TenantMemberDto> list(
            @PathVariable UUID tenantId,
            @RequestParam Optional<String> status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize
    ) {
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), clampPageSize(pageSize));
        Page<TenantUser> result = iamAdminService.listMembers(tenantId, status, pageable);
        return new PageResponse<>(
                result.getContent().stream().map(tu -> toDto(tenantId, tu)).toList(),
                page,
                result.getSize(),
                result.getTotalElements()
        );
    }

    @PostMapping
    @PreAuthorize("(hasAuthority('iam:tenant_users:write') and @iamSecurity.isTenant(#tenantId)) or hasAuthority('iam:security:admin') or @iamSecurity.isSuperAdmin()")
    public TenantMemberDtos.TenantMemberDto add(
            @PathVariable UUID tenantId,
            @Valid @RequestBody TenantMemberDtos.AddMemberRequest req
    ) {
        UUID userId = req.getUserId();
        if (userId == null) {
            // optional convenience: allow lookup by email when userId not provided
            if (req.getEmail() == null || req.getEmail().isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Provide either userId or email");
            }
            User u = iamAdminService.getUserByEmail(req.getEmail());
            userId = u.getId();
        }
        TenantUser tu = iamAdminService.addMember(tenantId, userId, req.getDisplayName(), req.getStatus());
        return toDto(tenantId, tu);
    }

    @GetMapping("/{userId}")
    @PreAuthorize("(hasAuthority('iam:tenant_users:read') and @iamSecurity.isTenant(#tenantId)) or hasAuthority('iam:security:admin') or @iamSecurity.isSuperAdmin()")
    public TenantMemberDtos.TenantMemberDto get(@PathVariable UUID tenantId, @PathVariable UUID userId) {
        TenantUser tu = iamAdminService.getMember(tenantId, userId);
        return toDto(tenantId, tu);
    }

    @PatchMapping("/{userId}")
    @PreAuthorize("(hasAuthority('iam:tenant_users:write') and @iamSecurity.isTenant(#tenantId)) or hasAuthority('iam:security:admin') or @iamSecurity.isSuperAdmin()")
    public TenantMemberDtos.TenantMemberDto update(
            @PathVariable UUID tenantId,
            @PathVariable UUID userId,
            @Valid @RequestBody TenantMemberDtos.UpdateMemberRequest req
    ) {
        TenantUser tu = iamAdminService.updateMember(
                tenantId,
                userId,
                Optional.ofNullable(req.getDisplayName()),
                Optional.ofNullable(req.getStatus())
        );
        return toDto(tenantId, tu);
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("(hasAuthority('iam:tenant_users:write') and @iamSecurity.isTenant(#tenantId)) or hasAuthority('iam:security:admin') or @iamSecurity.isSuperAdmin()")
    public void delete(@PathVariable UUID tenantId, @PathVariable UUID userId) {
        iamAdminService.deleteMember(tenantId, userId);
    }

    private TenantMemberDtos.TenantMemberDto toDto(UUID tenantId, TenantUser tu) {
        // tenant/user relations are LAZY; rely on ids and keep extra fields nullable
        return new TenantMemberDtos.TenantMemberDto(
                tenantId,
                tu.getUserId(),
                null,
                null,
                tu.getDisplayName(),
                tu.getStatus(),
                tu.getInvitedAt(),
                tu.getJoinedAt(),
                tu.getCreatedAt(),
                tu.getUpdatedAt()
        );
    }

    private static int clampPageSize(int pageSize) {
        if (pageSize <= 0) return 50;
        return Math.min(pageSize, 200);
    }
}


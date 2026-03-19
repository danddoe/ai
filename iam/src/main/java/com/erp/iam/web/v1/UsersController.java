package com.erp.iam.web.v1;

import com.erp.iam.domain.User;
import com.erp.iam.service.IamAdminService;
import com.erp.iam.web.v1.dto.PageResponse;
import com.erp.iam.web.v1.dto.UserDtos;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/v1/users")
public class UsersController {

    private final IamAdminService iamAdminService;

    public UsersController(IamAdminService iamAdminService) {
        this.iamAdminService = iamAdminService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('iam:security:admin') or @iamSecurity.isSuperAdmin()")
    public PageResponse<UserDtos.UserDto> list(
            @RequestParam Optional<String> email,
            @RequestParam Optional<String> status,
            @RequestParam Optional<String> q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize
    ) {
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), clampPageSize(pageSize));
        Page<User> result = iamAdminService.listUsers(email, status, q, pageable);
        return new PageResponse<>(
                result.getContent().stream().map(UsersController::toDto).toList(),
                page,
                result.getSize(),
                result.getTotalElements()
        );
    }

    @PostMapping
    @PreAuthorize("hasAuthority('iam:security:admin') or @iamSecurity.isSuperAdmin()")
    public UserDtos.UserDto create(@Valid @RequestBody UserDtos.CreateUserRequest req) {
        User u = iamAdminService.createUser(req.getEmail(), req.getDisplayName(), req.getPassword(), req.getStatus());
        return toDto(u);
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasAuthority('iam:security:admin') or @iamSecurity.isSuperAdmin()")
    public UserDtos.UserDto get(@PathVariable UUID userId) {
        return toDto(iamAdminService.getUser(userId));
    }

    @PatchMapping("/{userId}")
    @PreAuthorize("hasAuthority('iam:security:admin') or @iamSecurity.isSuperAdmin()")
    public UserDtos.UserDto update(@PathVariable UUID userId, @Valid @RequestBody UserDtos.UpdateUserRequest req) {
        User u = iamAdminService.updateUser(
                userId,
                Optional.ofNullable(req.getEmail()),
                Optional.ofNullable(req.getDisplayName()),
                Optional.ofNullable(req.getStatus()),
                Optional.ofNullable(req.getPassword())
        );
        return toDto(u);
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAuthority('iam:security:admin') or @iamSecurity.isSuperAdmin()")
    public void delete(@PathVariable UUID userId) {
        iamAdminService.deleteUser(userId);
    }

    private static UserDtos.UserDto toDto(User u) {
        return new UserDtos.UserDto(
                u.getId(),
                u.getEmail(),
                u.getDisplayName(),
                u.getStatus(),
                u.getEmailVerifiedAt(),
                u.getLastLoginAt(),
                u.getCreatedAt(),
                u.getUpdatedAt()
        );
    }

    private static int clampPageSize(int pageSize) {
        if (pageSize <= 0) return 50;
        return Math.min(pageSize, 200);
    }
}


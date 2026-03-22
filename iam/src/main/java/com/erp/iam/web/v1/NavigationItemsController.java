package com.erp.iam.web.v1;

import com.erp.iam.security.TenantPrincipal;
import com.erp.iam.service.PortalNavigationAdminService;
import com.erp.iam.web.v1.dto.NavigationDtos;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/navigation/items")
public class NavigationItemsController {

    private final PortalNavigationAdminService adminService;

    public NavigationItemsController(PortalNavigationAdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NavigationDtos.NavigationAdminListResponse> list() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        TenantPrincipal principal = (TenantPrincipal) auth.getPrincipal();
        NavigationDtos.NavigationAdminListResponse body = adminService.listForPrincipal(auth, principal);
        return ResponseEntity.ok(body);
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NavigationDtos.NavigationItemCreatedDto> create(@Valid @RequestBody NavigationDtos.NavigationItemCreateRequest req) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        TenantPrincipal principal = (TenantPrincipal) auth.getPrincipal();
        NavigationDtos.NavigationItemCreatedDto created = adminService.create(auth, principal, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> patch(
            @PathVariable UUID id,
            @RequestBody NavigationDtos.NavigationItemPatchRequest req
    ) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        TenantPrincipal principal = (TenantPrincipal) auth.getPrincipal();
        adminService.update(auth, principal, id, req);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        TenantPrincipal principal = (TenantPrincipal) auth.getPrincipal();
        adminService.deactivate(auth, principal, id);
        return ResponseEntity.noContent().build();
    }
}

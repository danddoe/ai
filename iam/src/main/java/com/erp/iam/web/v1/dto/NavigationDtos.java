package com.erp.iam.web.v1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public class NavigationDtos {

    public record NavigationResponse(List<NavigationItemDto> items) {}

    /** TENANT (default) or GLOBAL (platform admin only). */
    public record NavigationItemCreateRequest(
            UUID parentId,
            int sortOrder,
            String routePath,
            @NotBlank String label,
            String description,
            String type,
            String icon,
            String categoryKey,
            List<String> searchKeywords,
            List<String> requiredPermissions,
            List<String> requiredRoles,
            @NotNull String scope
    ) {}

    public record NavigationItemCreatedDto(UUID id) {}

    public record NavigationItemPatchRequest(
            UUID parentId,
            Integer sortOrder,
            String routePath,
            String label,
            String description,
            String icon,
            String categoryKey,
            String type,
            List<String> searchKeywords,
            List<String> requiredPermissions,
            List<String> requiredRoles,
            Boolean active,
            Boolean promoteToGlobal
    ) {}

    public record NavigationItemDto(
            UUID id,
            UUID parentId,
            int sortOrder,
            String routePath,
            String label,
            String description,
            String type,
            String icon,
            String categoryKey,
            List<String> searchKeywords,
            List<NavigationItemDto> children
    ) {}

    /** Flat admin list (editable rows only; scope enforced in service). */
    public record NavigationAdminListResponse(List<NavigationAdminItemDto> items) {}

    public record NavigationAdminItemDto(
            UUID id,
            UUID parentId,
            UUID tenantId,
            int sortOrder,
            String routePath,
            String label,
            String description,
            String type,
            String icon,
            String categoryKey,
            List<String> searchKeywords,
            List<String> requiredPermissions,
            List<String> requiredRoles,
            boolean active
    ) {}

    /** Flat hits for omnibox / Cmd+K (same RBAC visibility as full tree). */
    public record NavigationSearchResponse(List<NavigationSearchHitDto> items) {}

    public record NavigationSearchHitDto(
            UUID id,
            String label,
            String description,
            String routePath,
            String type,
            String icon,
            String categoryKey
    ) {}
}

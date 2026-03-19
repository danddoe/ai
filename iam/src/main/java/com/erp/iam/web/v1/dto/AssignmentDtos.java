package com.erp.iam.web.v1.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public class AssignmentDtos {

    public static class ReplaceRolePermissionsRequest {
        @NotNull
        private List<UUID> permissionIds;

        public List<UUID> getPermissionIds() { return permissionIds; }
        public void setPermissionIds(List<UUID> permissionIds) { this.permissionIds = permissionIds; }
    }

    public static class ReplaceUserRolesRequest {
        @NotNull
        private List<UUID> roleIds;

        public List<UUID> getRoleIds() { return roleIds; }
        public void setRoleIds(List<UUID> roleIds) { this.roleIds = roleIds; }
    }
}


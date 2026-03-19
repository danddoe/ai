package com.erp.iam.repository;

import com.erp.iam.domain.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface RolePermissionRepository extends JpaRepository<RolePermission, UUID> {

    List<RolePermission> findByTenantIdAndRoleId(UUID tenantId, UUID roleId);

    void deleteByTenantIdAndRoleId(UUID tenantId, UUID roleId);

    @Query("SELECT rp.permissionId FROM RolePermission rp WHERE rp.tenantId = :tenantId AND rp.roleId = :roleId")
    List<UUID> findPermissionIdsByTenantIdAndRoleId(UUID tenantId, UUID roleId);
}

package com.erp.iam.repository;

import com.erp.iam.domain.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {

    List<UserRole> findByTenantIdAndUserId(UUID tenantId, UUID userId);

    boolean existsByTenantIdAndUserIdAndRoleId(UUID tenantId, UUID userId, UUID roleId);

    void deleteByTenantIdAndUserIdAndRoleId(UUID tenantId, UUID userId, UUID roleId);

    @Query("SELECT ur.roleId FROM UserRole ur WHERE ur.tenantId = :tenantId AND ur.userId = :userId")
    List<UUID> findRoleIdsByTenantIdAndUserId(UUID tenantId, UUID userId);
}

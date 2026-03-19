package com.erp.iam.repository;

import com.erp.iam.domain.TenantUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantUserRepository extends JpaRepository<TenantUser, UUID>, JpaSpecificationExecutor<TenantUser> {

    Optional<TenantUser> findByTenantIdAndUserId(UUID tenantId, UUID userId);

    List<TenantUser> findByTenantId(UUID tenantId);

    List<TenantUser> findByUserId(UUID userId);

    boolean existsByTenantIdAndUserId(UUID tenantId, UUID userId);
}

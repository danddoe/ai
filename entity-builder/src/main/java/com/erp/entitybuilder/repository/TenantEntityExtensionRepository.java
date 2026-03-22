package com.erp.entitybuilder.repository;

import com.erp.entitybuilder.domain.TenantEntityExtension;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TenantEntityExtensionRepository extends JpaRepository<TenantEntityExtension, UUID> {

    List<TenantEntityExtension> findByTenantIdAndBaseEntityIdOrderByNameAsc(UUID tenantId, UUID baseEntityId);

    boolean existsByTenantIdAndSlug(UUID tenantId, String slug);
}

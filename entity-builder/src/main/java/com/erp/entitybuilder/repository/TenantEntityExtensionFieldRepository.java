package com.erp.entitybuilder.repository;

import com.erp.entitybuilder.domain.TenantEntityExtensionField;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TenantEntityExtensionFieldRepository extends JpaRepository<TenantEntityExtensionField, UUID> {

    Optional<TenantEntityExtensionField> findByTenantEntityExtensionIdAndSlug(UUID tenantEntityExtensionId, String slug);

    void deleteByTenantEntityExtensionIdAndSlug(UUID tenantEntityExtensionId, String slug);

    boolean existsByTenantEntityExtensionIdAndSlug(UUID tenantEntityExtensionId, String slug);
}

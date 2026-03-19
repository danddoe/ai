package com.erp.entitybuilder.repository;

import com.erp.entitybuilder.domain.EntityDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EntityDefinitionRepository extends JpaRepository<EntityDefinition, UUID>, JpaSpecificationExecutor<EntityDefinition> {
    Optional<EntityDefinition> findByTenantIdAndSlug(UUID tenantId, String slug);

    boolean existsByTenantIdAndSlug(UUID tenantId, String slug);

    List<EntityDefinition> findAllByTenantIdOrderByNameAsc(UUID tenantId);
}


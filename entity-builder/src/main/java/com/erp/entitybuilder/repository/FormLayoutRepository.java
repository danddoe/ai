package com.erp.entitybuilder.repository;

import com.erp.entitybuilder.domain.FormLayout;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FormLayoutRepository extends JpaRepository<FormLayout, UUID> {
    List<FormLayout> findByTenantIdAndEntityId(UUID tenantId, UUID entityId);
    Optional<FormLayout> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<FormLayout> findByTenantIdAndEntityIdAndName(UUID tenantId, UUID entityId, String name);
}


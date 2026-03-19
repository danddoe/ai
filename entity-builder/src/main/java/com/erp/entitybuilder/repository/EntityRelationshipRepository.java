package com.erp.entitybuilder.repository;

import com.erp.entitybuilder.domain.EntityRelationship;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EntityRelationshipRepository extends JpaRepository<EntityRelationship, UUID> {
    List<EntityRelationship> findByTenantId(UUID tenantId);
    Optional<EntityRelationship> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<EntityRelationship> findByTenantIdAndSlug(UUID tenantId, String slug);
}


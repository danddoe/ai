package com.erp.entitybuilder.repository;

import com.erp.entitybuilder.domain.EntityField;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EntityFieldRepository extends JpaRepository<EntityField, UUID> {
    Optional<EntityField> findByEntityIdAndSlug(UUID entityId, String slug);
    List<EntityField> findByEntityId(UUID entityId);
    boolean existsByEntityIdAndSlug(UUID entityId, String slug);
}


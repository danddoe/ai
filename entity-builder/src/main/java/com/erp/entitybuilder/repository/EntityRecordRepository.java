package com.erp.entitybuilder.repository;

import com.erp.entitybuilder.domain.EntityRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface EntityRecordRepository extends JpaRepository<EntityRecord, UUID> {

    Optional<EntityRecord> findByTenantIdAndEntityIdAndExternalId(UUID tenantId, UUID entityId, String externalId);

    boolean existsByTenantIdAndEntityIdAndExternalId(UUID tenantId, UUID entityId, String externalId);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    Page<EntityRecord> findByTenantIdAndEntityId(UUID tenantId, UUID entityId, Pageable pageable);
}


package com.erp.entitybuilder.repository;

import com.erp.entitybuilder.domain.EntityRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EntityRecordRepository extends JpaRepository<EntityRecord, UUID> {

    Optional<EntityRecord> findByTenantIdAndEntityIdAndExternalId(UUID tenantId, UUID entityId, String externalId);

    Optional<EntityRecord> findByTenantIdAndEntityIdAndBusinessDocumentNumber(UUID tenantId, UUID entityId, String businessDocumentNumber);

    boolean existsByTenantIdAndEntityIdAndExternalId(UUID tenantId, UUID entityId, String externalId);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    Page<EntityRecord> findByTenantIdAndEntityId(UUID tenantId, UUID entityId, Pageable pageable);

    @Query(
            value = "SELECT id FROM entity_records WHERE tenant_id = ?1 AND entity_id = ?2 AND search_vector ILIKE ?3 ESCAPE '!' ORDER BY updated_at DESC LIMIT ?4",
            nativeQuery = true
    )
    List<UUID> findIdsForSearchLookup(UUID tenantId, UUID entityId, String ilikePattern, int limit);
}


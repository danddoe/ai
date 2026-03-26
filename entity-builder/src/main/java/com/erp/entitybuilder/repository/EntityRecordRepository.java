package com.erp.entitybuilder.repository;

import com.erp.entitybuilder.domain.EntityRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EntityRecordRepository extends JpaRepository<EntityRecord, UUID> {

    Optional<EntityRecord> findByTenantIdAndEntityIdAndExternalId(UUID tenantId, UUID entityId, String externalId);

    Optional<EntityRecord> findByTenantIdAndEntityIdAndBusinessDocumentNumber(UUID tenantId, UUID entityId, String businessDocumentNumber);

    boolean existsByTenantIdAndEntityIdAndExternalId(UUID tenantId, UUID entityId, String externalId);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    @Query("SELECT r FROM EntityRecord r WHERE r.tenantId = :tenantId AND r.entityId IN :entityIds")
    Page<EntityRecord> findByTenantIdAndEntityIdIn(
            @Param("tenantId") UUID tenantId,
            @Param("entityIds") List<UUID> entityIds,
            Pageable pageable
    );

    @Query(
            "SELECT r FROM EntityRecord r WHERE r.entityId IN :entityIds AND (r.tenantId = :requestTenantId OR "
                    + "(r.recordScope = com.erp.entitybuilder.domain.RecordScope.STANDARD_RECORD AND r.tenantId = :platformTenantId))"
    )
    Page<EntityRecord> findVisibleByEntityIdIn(
            @Param("entityIds") List<UUID> entityIds,
            @Param("requestTenantId") UUID requestTenantId,
            @Param("platformTenantId") UUID platformTenantId,
            Pageable pageable
    );

    @Query(
            "SELECT r FROM EntityRecord r WHERE r.entityId IN :entityIds AND (r.tenantId = :requestTenantId OR "
                    + "(r.recordScope = com.erp.entitybuilder.domain.RecordScope.STANDARD_RECORD AND r.tenantId = :platformTenantId)) "
                    + "AND (r.id IN :assignedRecordIds OR r.entityStatusId IN :assignedRecordIds)"
    )
    Page<EntityRecord> findVisibleByEntityIdInAndIdOrStatusIdIn(
            @Param("entityIds") List<UUID> entityIds,
            @Param("requestTenantId") UUID requestTenantId,
            @Param("platformTenantId") UUID platformTenantId,
            @Param("assignedRecordIds") List<UUID> assignedRecordIds,
            Pageable pageable
    );

    @Query(
            "SELECT r FROM EntityRecord r WHERE r.tenantId = :tenantId AND r.entityId IN :entityIds "
                    + "AND (r.id IN :assignedRecordIds OR r.entityStatusId IN :assignedRecordIds)"
    )
    Page<EntityRecord> findByTenantIdAndEntityIdInAndIdOrStatusIdIn(
            @Param("tenantId") UUID tenantId,
            @Param("entityIds") List<UUID> entityIds,
            @Param("assignedRecordIds") List<UUID> assignedRecordIds,
            Pageable pageable
    );

    @Query(
            value = "SELECT id FROM entity_records WHERE entity_id IN (:entityIds) AND (tenant_id = :requestTenant OR (record_scope = 'STANDARD_RECORD' AND tenant_id = :platformTenant)) AND (id IN (:assignedIds) OR entity_status_id IN (:assignedIds)) AND search_vector ILIKE :pattern ESCAPE '!' ORDER BY updated_at DESC LIMIT :lim",
            nativeQuery = true
    )
    List<UUID> findIdsForSearchLookupVisibleAssigned(
            @Param("requestTenant") UUID requestTenantId,
            @Param("entityIds") List<UUID> entityIds,
            @Param("pattern") String ilikePattern,
            @Param("lim") int limit,
            @Param("platformTenant") UUID platformTenantId,
            @Param("assignedIds") List<UUID> assignedIds
    );

    @Query(
            value = "SELECT id FROM entity_records WHERE tenant_id = :tenantId AND entity_id IN (:entityIds) AND (id IN (:assignedIds) OR entity_status_id IN (:assignedIds)) AND search_vector ILIKE :pattern ESCAPE '!' ORDER BY updated_at DESC LIMIT :lim",
            nativeQuery = true
    )
    List<UUID> findIdsForSearchLookupAssigned(
            @Param("tenantId") UUID tenantId,
            @Param("entityIds") List<UUID> entityIds,
            @Param("pattern") String ilikePattern,
            @Param("lim") int limit,
            @Param("assignedIds") List<UUID> assignedIds
    );

    @Query(
            value = "SELECT id FROM entity_records WHERE entity_id IN (:entityIds) AND (tenant_id = :requestTenant OR (record_scope = 'STANDARD_RECORD' AND tenant_id = :platformTenant)) AND search_vector ILIKE :pattern ESCAPE '!' ORDER BY updated_at DESC LIMIT :lim",
            nativeQuery = true
    )
    List<UUID> findIdsForSearchLookupVisible(
            @Param("requestTenant") UUID requestTenantId,
            @Param("entityIds") List<UUID> entityIds,
            @Param("pattern") String ilikePattern,
            @Param("lim") int limit,
            @Param("platformTenant") UUID platformTenantId
    );

    @Query(
            value = "SELECT id FROM entity_records WHERE tenant_id = :tenantId AND entity_id IN (:entityIds) AND search_vector ILIKE :pattern ESCAPE '!' ORDER BY updated_at DESC LIMIT :lim",
            nativeQuery = true
    )
    List<UUID> findIdsForSearchLookup(
            @Param("tenantId") UUID tenantId,
            @Param("entityIds") List<UUID> entityIds,
            @Param("pattern") String ilikePattern,
            @Param("lim") int limit
    );
}

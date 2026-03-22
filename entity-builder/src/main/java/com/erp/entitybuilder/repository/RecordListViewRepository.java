package com.erp.entitybuilder.repository;

import com.erp.entitybuilder.domain.RecordListView;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecordListViewRepository extends JpaRepository<RecordListView, UUID> {
    List<RecordListView> findByTenantIdAndEntityId(UUID tenantId, UUID entityId);

    Optional<RecordListView> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<RecordListView> findByTenantIdAndEntityIdAndName(UUID tenantId, UUID entityId, String name);
}

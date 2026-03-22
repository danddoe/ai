package com.erp.coreservice.repository;

import com.erp.coreservice.domain.BuHierarchyHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BuHierarchyHistoryRepository extends JpaRepository<BuHierarchyHistory, UUID> {

    Optional<BuHierarchyHistory> findByTenantIdAndChildBuIdAndEndDateIsNull(UUID tenantId, UUID childBuId);
}

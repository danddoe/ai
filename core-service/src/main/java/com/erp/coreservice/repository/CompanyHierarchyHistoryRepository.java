package com.erp.coreservice.repository;

import com.erp.coreservice.domain.CompanyHierarchyHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CompanyHierarchyHistoryRepository extends JpaRepository<CompanyHierarchyHistory, UUID> {

    Optional<CompanyHierarchyHistory> findByTenantIdAndChildCompanyIdAndEndDateIsNull(UUID tenantId, UUID childCompanyId);
}

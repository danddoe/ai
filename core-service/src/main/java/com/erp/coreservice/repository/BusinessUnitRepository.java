package com.erp.coreservice.repository;

import com.erp.coreservice.domain.BusinessUnit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BusinessUnitRepository extends JpaRepository<BusinessUnit, UUID> {

    Optional<BusinessUnit> findByBuIdAndTenantId(UUID buId, UUID tenantId);

    Page<BusinessUnit> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    Page<BusinessUnit> findByTenantIdAndCompanyIdOrderByCreatedAtDesc(UUID tenantId, UUID companyId, Pageable pageable);
}

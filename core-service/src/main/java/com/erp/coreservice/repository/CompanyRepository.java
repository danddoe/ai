package com.erp.coreservice.repository;

import com.erp.coreservice.domain.Company;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CompanyRepository extends JpaRepository<Company, UUID> {

    Optional<Company> findByCompanyIdAndTenantId(UUID companyId, UUID tenantId);

    Page<Company> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    boolean existsByTenantIdAndParentCompanyId(UUID tenantId, UUID parentCompanyId);
}

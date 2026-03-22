package com.erp.loansmodule.repository;

import com.erp.loansmodule.domain.LoanApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LoanApplicationRepository extends JpaRepository<LoanApplication, UUID> {

    Optional<LoanApplication> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<LoanApplication> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);
}
